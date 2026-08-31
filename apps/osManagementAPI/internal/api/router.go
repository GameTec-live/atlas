package api

import (
	"context"
	"crypto/sha256"
	"crypto/subtle"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log/slog"
	"mime"
	"mime/multipart"
	"net/http"
	"os"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"github.com/GameTec-live/atlas/apps/osManagementAPI/internal/networkmanager"
	"github.com/GameTec-live/atlas/apps/osManagementAPI/internal/update"
)

type UpdateManager interface {
	Status(context.Context) (update.Status, error)
	Apply(context.Context, string) error
	Rollback(context.Context) error
}

type Monitor interface {
	Status() update.MonitorStatus
}

type PowerManager interface {
	Reboot(context.Context) error
	RebootTryboot(context.Context) error
	Poweroff(context.Context) error
}

type ResetRequester interface {
	Request() error
}

type SSHManager interface {
	Status(context.Context) (bool, error)
	Enable(context.Context) error
	Disable(context.Context) error
}

type NetworkManager interface {
	Connections(context.Context) ([]networkmanager.Connection, error)
	Devices(context.Context) ([]networkmanager.Device, error)
	IPSettings(context.Context, string) (networkmanager.IPSettings, error)
	SetIPSettings(context.Context, string, networkmanager.IPSettings) error
	Wifi(context.Context, string) ([]networkmanager.AccessPoint, error)
	ConnectWifi(context.Context, networkmanager.WifiRequest) error
	Disconnect(context.Context, string) error
	Forget(context.Context, string) error
}

type OriginsManager interface {
	List() ([]string, error)
	Add(context.Context, string) ([]string, error)
	Remove(context.Context, string) ([]string, error)
	RestartAPI(context.Context) error
}

type Scheduler interface {
	After(time.Duration, func())
}

type Dependencies struct {
	Token          string
	StateDir       string
	MaxUpdateBytes int64
	ShutdownDelay  time.Duration
	Update         UpdateManager
	Monitor        Monitor
	Power          PowerManager
	Reset          ResetRequester
	SSH            SSHManager
	Network        NetworkManager
	Origins        OriginsManager
	Scheduler      Scheduler
}

type handler struct {
	dependencies Dependencies
	mutations    sync.Mutex
	terminating  atomic.Bool
}

func NewRouter(dependencies Dependencies) http.Handler {
	h := &handler{dependencies: dependencies}
	router := http.NewServeMux()
	router.HandleFunc("GET /healthz", h.health)
	router.HandleFunc("GET /api/v1/update", h.updateStatus)
	router.HandleFunc("POST /api/v1/update", h.applyUpdate)
	router.HandleFunc("POST /api/v1/update/rollback", h.rollbackUpdate)
	router.HandleFunc("POST /api/v1/power/reboot", h.reboot)
	router.HandleFunc("POST /api/v1/power/poweroff", h.poweroff)
	router.HandleFunc("POST /api/v1/factory-reset", h.factoryReset)
	router.HandleFunc("GET /api/v1/ssh", h.sshStatus)
	router.HandleFunc("POST /api/v1/ssh/enable", h.enableSSH)
	router.HandleFunc("POST /api/v1/ssh/disable", h.disableSSH)
	router.HandleFunc("GET /api/v1/connections/adapters", h.adapters)
	router.HandleFunc("GET /api/v1/connections/network-manager", h.connections)
	router.HandleFunc("GET /api/v1/connections/network-manager/devices", h.devices)
	router.HandleFunc("GET /api/v1/connections/network-manager/wifi", h.wifi)
	router.HandleFunc("POST /api/v1/connections/network-manager/wifi", h.connectWifi)
	router.HandleFunc("GET /api/v1/connections/network-manager/{uuid}/ip", h.ipSettings)
	router.HandleFunc("PUT /api/v1/connections/network-manager/{uuid}/ip", h.setIPSettings)
	router.HandleFunc("POST /api/v1/connections/network-manager/{uuid}/disconnect", h.disconnect)
	router.HandleFunc("DELETE /api/v1/connections/network-manager/{uuid}", h.forget)
	router.HandleFunc("GET /api/v1/connections/auth-origins", h.origins)
	router.HandleFunc("POST /api/v1/connections/auth-origins", h.addOrigin)
	router.HandleFunc("DELETE /api/v1/connections/auth-origins", h.removeOrigin)
	return h.authenticate(router)
}

func (h *handler) authenticate(next http.Handler) http.Handler {
	expected := sha256.Sum256([]byte(h.dependencies.Token))
	return http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		provided, ok := strings.CutPrefix(request.Header.Get("Authorization"), "Bearer ")
		actual := sha256.Sum256([]byte(provided))
		if !ok || subtle.ConstantTimeCompare(expected[:], actual[:]) != 1 {
			fail(writer, http.StatusUnauthorized, "unauthorized", "a valid bearer token is required")
			return
		}
		next.ServeHTTP(writer, request)
	})
}

func (h *handler) health(writer http.ResponseWriter, _ *http.Request) {
	writeJSON(writer, http.StatusOK, map[string]string{"status": "ok"})
}

func (h *handler) updateStatus(writer http.ResponseWriter, request *http.Request) {
	status, err := h.dependencies.Update.Status(request.Context())
	if err != nil {
		fail(writer, http.StatusInternalServerError, "update_status_failed", err.Error())
		return
	}
	writeJSON(writer, http.StatusOK, map[string]any{"update": status, "monitor": h.dependencies.Monitor.Status()})
}

func (h *handler) applyUpdate(writer http.ResponseWriter, request *http.Request) {
	if !h.beginMutation(writer) {
		return
	}
	defer h.mutations.Unlock()

	bundlePath, err := h.receiveBundle(writer, request)
	if err != nil {
		fail(writer, http.StatusBadRequest, "invalid_update", err.Error())
		return
	}
	defer os.Remove(bundlePath)
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Hour)
	defer cancel()
	if err := h.dependencies.Update.Apply(ctx, bundlePath); err != nil {
		fail(writer, http.StatusUnprocessableEntity, "update_failed", err.Error())
		return
	}
	h.terminating.Store(true)
	writeJSON(writer, http.StatusAccepted, map[string]string{"status": "rebooting_into_candidate"})
	h.schedule("tryboot reboot", h.dependencies.Power.RebootTryboot)
}

func (h *handler) receiveBundle(writer http.ResponseWriter, request *http.Request) (string, error) {
	mediaType, parameters, err := mime.ParseMediaType(request.Header.Get("Content-Type"))
	if err != nil || mediaType != "multipart/form-data" {
		return "", fmt.Errorf("content type must be multipart/form-data")
	}
	request.Body = http.MaxBytesReader(writer, request.Body, h.dependencies.MaxUpdateBytes+1<<20)
	reader := multipart.NewReader(request.Body, parameters["boundary"])
	if err := os.MkdirAll(h.dependencies.StateDir, 0o700); err != nil {
		return "", err
	}
	temporary, err := os.CreateTemp(h.dependencies.StateDir, "update-upload.*.tar.zst")
	if err != nil {
		return "", err
	}
	path := temporary.Name()
	keep := false
	defer func() {
		temporary.Close()
		if !keep {
			os.Remove(path)
		}
	}()
	if err := temporary.Chmod(0o600); err != nil {
		return "", err
	}

	found := false
	for {
		part, nextErr := reader.NextPart()
		if errors.Is(nextErr, io.EOF) {
			break
		}
		if nextErr != nil {
			return "", nextErr
		}
		if part.FormName() != "bundle" || part.FileName() == "" || found {
			part.Close()
			return "", fmt.Errorf("multipart request must contain exactly one bundle file")
		}
		found = true
		written, copyErr := io.Copy(temporary, io.LimitReader(part, h.dependencies.MaxUpdateBytes+1))
		part.Close()
		if copyErr != nil {
			return "", copyErr
		}
		if written > h.dependencies.MaxUpdateBytes {
			return "", fmt.Errorf("update bundle exceeds %d bytes", h.dependencies.MaxUpdateBytes)
		}
	}
	if !found {
		return "", fmt.Errorf("multipart request is missing bundle")
	}
	if err := temporary.Sync(); err != nil {
		return "", err
	}
	if err := temporary.Close(); err != nil {
		return "", err
	}
	keep = true
	return path, nil
}

func (h *handler) rollbackUpdate(writer http.ResponseWriter, _ *http.Request) {
	if !h.beginMutation(writer) {
		return
	}
	defer h.mutations.Unlock()
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()
	if err := h.dependencies.Update.Rollback(ctx); err != nil {
		fail(writer, http.StatusBadRequest, "rollback_failed", err.Error())
		return
	}
	writeJSON(writer, http.StatusOK, map[string]string{"status": "ok"})
}

func (h *handler) reboot(writer http.ResponseWriter, _ *http.Request) {
	h.schedulePower(writer, "rebooting", "reboot", h.dependencies.Power.Reboot)
}

func (h *handler) poweroff(writer http.ResponseWriter, _ *http.Request) {
	h.schedulePower(writer, "powering_off", "poweroff", h.dependencies.Power.Poweroff)
}

func (h *handler) factoryReset(writer http.ResponseWriter, request *http.Request) {
	if !h.beginMutation(writer) {
		return
	}
	defer h.mutations.Unlock()
	status, err := h.dependencies.Update.Status(request.Context())
	if err != nil {
		fail(writer, http.StatusInternalServerError, "factory_reset_failed", err.Error())
		return
	}
	if status.Pending != "" {
		fail(writer, http.StatusConflict, "update_pending", "commit or roll back the pending update before factory reset")
		return
	}
	if err := h.dependencies.Reset.Request(); err != nil {
		fail(writer, http.StatusInternalServerError, "factory_reset_failed", err.Error())
		return
	}
	h.terminating.Store(true)
	writeJSON(writer, http.StatusAccepted, map[string]string{"status": "factory_reset_scheduled"})
	h.schedule("factory-reset reboot", h.dependencies.Power.Reboot)
}

func (h *handler) sshStatus(writer http.ResponseWriter, request *http.Request) {
	enabled, err := h.dependencies.SSH.Status(request.Context())
	if err != nil {
		fail(writer, http.StatusInternalServerError, "ssh_status_failed", err.Error())
		return
	}
	writeJSON(writer, http.StatusOK, map[string]bool{"enabled": enabled})
}

func (h *handler) enableSSH(writer http.ResponseWriter, request *http.Request) {
	h.mutate(writer, request, "ssh_enable_failed", h.dependencies.SSH.Enable)
}

func (h *handler) disableSSH(writer http.ResponseWriter, request *http.Request) {
	h.mutate(writer, request, "ssh_disable_failed", h.dependencies.SSH.Disable)
}

func (h *handler) adapters(writer http.ResponseWriter, _ *http.Request) {
	writeJSON(writer, http.StatusOK, map[string]any{"items": []map[string]string{{"id": "network-manager", "status": "available"}, {"id": "auth-origins", "status": "available"}, {"id": "cloudflare-tunnel", "status": "placeholder"}}})
}

func (h *handler) connections(writer http.ResponseWriter, request *http.Request) {
	items, err := h.dependencies.Network.Connections(request.Context())
	if err != nil {
		fail(writer, http.StatusInternalServerError, "network_manager_failed", err.Error())
		return
	}
	writeJSON(writer, http.StatusOK, map[string]any{"items": items, "count": len(items)})
}

func (h *handler) devices(writer http.ResponseWriter, request *http.Request) {
	items, err := h.dependencies.Network.Devices(request.Context())
	if err != nil {
		fail(writer, http.StatusInternalServerError, "network_manager_failed", err.Error())
		return
	}
	writeJSON(writer, http.StatusOK, map[string]any{"items": items, "count": len(items)})
}

func (h *handler) wifi(writer http.ResponseWriter, request *http.Request) {
	items, err := h.dependencies.Network.Wifi(request.Context(), request.URL.Query().Get("device"))
	if err != nil {
		fail(writer, http.StatusInternalServerError, "wifi_scan_failed", err.Error())
		return
	}
	writeJSON(writer, http.StatusOK, map[string]any{"items": items, "count": len(items)})
}

func (h *handler) connectWifi(writer http.ResponseWriter, request *http.Request) {
	var body networkmanager.WifiRequest
	if !decodeJSON(writer, request, &body) {
		return
	}
	h.mutate(writer, request, "wifi_connection_failed", func(ctx context.Context) error { return h.dependencies.Network.ConnectWifi(ctx, body) })
}

func (h *handler) ipSettings(writer http.ResponseWriter, request *http.Request) {
	settings, err := h.dependencies.Network.IPSettings(request.Context(), request.PathValue("uuid"))
	if err != nil {
		fail(writer, http.StatusBadRequest, "ip_settings_failed", err.Error())
		return
	}
	writeJSON(writer, http.StatusOK, settings)
}

func (h *handler) setIPSettings(writer http.ResponseWriter, request *http.Request) {
	var body networkmanager.IPSettings
	if !decodeJSON(writer, request, &body) {
		return
	}
	h.mutate(writer, request, "ip_settings_failed", func(ctx context.Context) error {
		return h.dependencies.Network.SetIPSettings(ctx, request.PathValue("uuid"), body)
	})
}

func (h *handler) disconnect(writer http.ResponseWriter, request *http.Request) {
	h.mutate(writer, request, "disconnect_failed", func(ctx context.Context) error {
		return h.dependencies.Network.Disconnect(ctx, request.PathValue("uuid"))
	})
}

func (h *handler) forget(writer http.ResponseWriter, request *http.Request) {
	h.mutate(writer, request, "forget_failed", func(ctx context.Context) error { return h.dependencies.Network.Forget(ctx, request.PathValue("uuid")) })
}

func (h *handler) origins(writer http.ResponseWriter, _ *http.Request) {
	items, err := h.dependencies.Origins.List()
	if err != nil {
		fail(writer, http.StatusInternalServerError, "origins_failed", err.Error())
		return
	}
	writeJSON(writer, http.StatusOK, map[string]any{"items": items, "count": len(items)})
}

type originRequest struct {
	Origin string `json:"origin"`
}

func (h *handler) addOrigin(writer http.ResponseWriter, request *http.Request) {
	h.changeOrigin(writer, request, h.dependencies.Origins.Add)
}

func (h *handler) removeOrigin(writer http.ResponseWriter, request *http.Request) {
	h.changeOrigin(writer, request, h.dependencies.Origins.Remove)
}

func (h *handler) changeOrigin(writer http.ResponseWriter, request *http.Request, change func(context.Context, string) ([]string, error)) {
	var body originRequest
	if !decodeJSON(writer, request, &body) {
		return
	}
	if !h.beginMutation(writer) {
		return
	}
	defer h.mutations.Unlock()
	items, err := change(request.Context(), body.Origin)
	if err != nil {
		fail(writer, http.StatusBadRequest, "origins_failed", err.Error())
		return
	}
	writeJSON(writer, http.StatusOK, map[string]any{"items": items, "count": len(items)})
	h.schedule("Atlas API restart after trusted-origin change", h.dependencies.Origins.RestartAPI)
}

func (h *handler) mutate(writer http.ResponseWriter, request *http.Request, code string, operation func(context.Context) error) {
	if !h.beginMutation(writer) {
		return
	}
	defer h.mutations.Unlock()
	if err := operation(request.Context()); err != nil {
		fail(writer, http.StatusBadRequest, code, err.Error())
		return
	}
	writeJSON(writer, http.StatusOK, map[string]string{"status": "ok"})
}

func (h *handler) beginTermination(writer http.ResponseWriter) bool {
	if !h.terminating.CompareAndSwap(false, true) {
		fail(writer, http.StatusConflict, "shutdown_pending", "the system is already shutting down")
		return false
	}
	return true
}

func (h *handler) beginMutation(writer http.ResponseWriter) bool {
	if !h.mutations.TryLock() {
		fail(writer, http.StatusConflict, "operation_in_progress", "another privileged operation is in progress")
		return false
	}
	if h.terminating.Load() {
		h.mutations.Unlock()
		fail(writer, http.StatusConflict, "shutdown_pending", "the system is shutting down")
		return false
	}
	return true
}

func (h *handler) schedulePower(writer http.ResponseWriter, status, name string, operation func(context.Context) error) {
	if !h.mutations.TryLock() {
		fail(writer, http.StatusConflict, "operation_in_progress", "another privileged operation is in progress")
		return
	}
	defer h.mutations.Unlock()
	if !h.beginTermination(writer) {
		return
	}
	writeJSON(writer, http.StatusAccepted, map[string]string{"status": status})
	h.schedule(name, operation)
}

func (h *handler) schedule(name string, operation func(context.Context) error) {
	h.dependencies.Scheduler.After(h.dependencies.ShutdownDelay, func() {
		ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
		defer cancel()
		if err := operation(ctx); err != nil {
			slog.Error("scheduled system operation failed", "operation", name, "error", err)
		}
	})
}

func decodeJSON(writer http.ResponseWriter, request *http.Request, destination any) bool {
	request.Body = http.MaxBytesReader(writer, request.Body, 1<<20)
	decoder := json.NewDecoder(request.Body)
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(destination); err != nil {
		fail(writer, http.StatusBadRequest, "invalid_request", err.Error())
		return false
	}
	if err := decoder.Decode(&struct{}{}); !errors.Is(err, io.EOF) {
		fail(writer, http.StatusBadRequest, "invalid_request", "request body must contain one JSON object")
		return false
	}
	return true
}

func writeJSON(writer http.ResponseWriter, status int, value any) {
	writer.Header().Set("Content-Type", "application/json")
	writer.WriteHeader(status)
	if err := json.NewEncoder(writer).Encode(value); err != nil {
		slog.Error("write JSON response", "error", err)
	}
}

func fail(writer http.ResponseWriter, status int, code, message string) {
	writeJSON(writer, status, map[string]any{"error": map[string]string{"code": code, "message": message}})
}
