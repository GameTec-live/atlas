package api

import (
	"bytes"
	"context"
	"encoding/json"
	"mime/multipart"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/GameTec-live/atlas/apps/osManagementAPI/internal/health"
	"github.com/GameTec-live/atlas/apps/osManagementAPI/internal/networkmanager"
	"github.com/GameTec-live/atlas/apps/osManagementAPI/internal/remoteaccess"
	"github.com/GameTec-live/atlas/apps/osManagementAPI/internal/update"
)

type fakeUpdate struct {
	applied []byte
}

func (u *fakeUpdate) Status(context.Context) (update.Status, error) {
	return update.Status{Active: "system_a", Other: "system_b"}, nil
}

func (u *fakeUpdate) Apply(_ context.Context, path string) error {
	data, err := os.ReadFile(path)
	u.applied = data
	return err
}

func (*fakeUpdate) Rollback(context.Context) error { return nil }

type fakeMonitor struct{}

func (fakeMonitor) Status() update.MonitorStatus { return update.MonitorStatus{Phase: "idle"} }

type fakeContainers struct {
	items []health.Container
}

func (c fakeContainers) RunningContainers(context.Context) ([]health.Container, error) {
	return c.items, nil
}

type fakePower struct {
	tryboot bool
}

func (*fakePower) Reboot(context.Context) error          { return nil }
func (p *fakePower) RebootTryboot(context.Context) error { p.tryboot = true; return nil }
func (*fakePower) Poweroff(context.Context) error        { return nil }

type fakeReset struct{}

func (fakeReset) Request() error { return nil }

type fakeSSH struct {
	enabled bool
}

func (s *fakeSSH) Status(context.Context) (bool, error) { return s.enabled, nil }
func (s *fakeSSH) Enable(context.Context) error         { s.enabled = true; return nil }
func (s *fakeSSH) Disable(context.Context) error        { s.enabled = false; return nil }

type fakeTimezone struct {
	value string
}

func (t *fakeTimezone) Status(context.Context) (string, error) { return t.value, nil }
func (t *fakeTimezone) Set(_ context.Context, value string) error {
	t.value = value
	return nil
}

type fakeNetwork struct{}

func (fakeNetwork) Connections(context.Context) ([]networkmanager.Connection, error) { return nil, nil }
func (fakeNetwork) Devices(context.Context) ([]networkmanager.Device, error)         { return nil, nil }
func (fakeNetwork) IPSettings(context.Context, string) (networkmanager.IPSettings, error) {
	return networkmanager.IPSettings{}, nil
}
func (fakeNetwork) SetIPSettings(context.Context, string, networkmanager.IPSettings) error {
	return nil
}
func (fakeNetwork) Wifi(context.Context, string) ([]networkmanager.AccessPoint, error) {
	return nil, nil
}
func (fakeNetwork) ConnectWifi(context.Context, networkmanager.WifiRequest) error { return nil }
func (fakeNetwork) Disconnect(context.Context, string) error                      { return nil }
func (fakeNetwork) Forget(context.Context, string) error                          { return nil }

type fakeOrigins struct{}

func (fakeOrigins) List() ([]string, error)                          { return nil, nil }
func (fakeOrigins) Add(context.Context, string) ([]string, error)    { return nil, nil }
func (fakeOrigins) Remove(context.Context, string) ([]string, error) { return nil, nil }
func (fakeOrigins) RestartAPI(context.Context) error                 { return nil }

type fakeRemoteAccess struct {
	status remoteaccess.Status
}

func (r *fakeRemoteAccess) Status(context.Context) (remoteaccess.Status, error) {
	return r.status, nil
}
func (r *fakeRemoteAccess) ProvisionCloudflare(_ context.Context, _ remoteaccess.CloudflareRequest) error {
	r.status.CloudflareTunnel = remoteaccess.ProviderStatus{Provisioned: true, State: "active", Detail: "running"}
	return nil
}
func (r *fakeRemoteAccess) RemoveCloudflare(context.Context) error {
	r.status.CloudflareTunnel = remoteaccess.ProviderStatus{State: "not_provisioned"}
	return nil
}
func (r *fakeRemoteAccess) ProvisionTailscale(_ context.Context, _ remoteaccess.TailscaleRequest) error {
	r.status.Tailscale = remoteaccess.ProviderStatus{Provisioned: true, State: "active", Detail: "running"}
	return nil
}
func (r *fakeRemoteAccess) RemoveTailscale(context.Context) error {
	r.status.Tailscale = remoteaccess.ProviderStatus{State: "not_provisioned"}
	return nil
}

type immediateScheduler struct{}

func (immediateScheduler) After(_ time.Duration, operation func()) { operation() }

func testRouter(stateDir string, updateManager *fakeUpdate, powerManager *fakePower) http.Handler {
	return NewRouter(Dependencies{
		Token:          "secret-token",
		StateDir:       stateDir,
		MaxUpdateBytes: 1024,
		Update:         updateManager,
		Monitor:        fakeMonitor{},
		Containers:     fakeContainers{},
		Power:          powerManager,
		Reset:          fakeReset{},
		SSH:            &fakeSSH{},
		Timezone:       &fakeTimezone{value: "Etc/UTC"},
		Network:        fakeNetwork{},
		Origins:        fakeOrigins{},
		RemoteAccess:   &fakeRemoteAccess{},
		Scheduler:      immediateScheduler{},
	})
}

func TestSSHStatusEnableAndDisable(t *testing.T) {
	sshManager := &fakeSSH{}
	router := NewRouter(Dependencies{
		Token:          "secret-token",
		StateDir:       t.TempDir(),
		MaxUpdateBytes: 1024,
		Update:         &fakeUpdate{},
		Monitor:        fakeMonitor{},
		Containers:     fakeContainers{},
		Power:          &fakePower{},
		Reset:          fakeReset{},
		SSH:            sshManager,
		Timezone:       &fakeTimezone{value: "Etc/UTC"},
		Network:        fakeNetwork{},
		Origins:        fakeOrigins{},
		RemoteAccess:   &fakeRemoteAccess{},
		Scheduler:      immediateScheduler{},
	})

	request := authenticatedRequest(http.MethodPost, "/api/v1/ssh/enable")
	response := httptest.NewRecorder()
	router.ServeHTTP(response, request)
	if response.Code != http.StatusOK || !sshManager.enabled {
		t.Fatalf("enable failed: code=%d body=%s enabled=%v", response.Code, response.Body.String(), sshManager.enabled)
	}

	request = authenticatedRequest(http.MethodGet, "/api/v1/ssh")
	response = httptest.NewRecorder()
	router.ServeHTTP(response, request)
	if response.Code != http.StatusOK || response.Body.String() != "{\"enabled\":true}\n" {
		t.Fatalf("unexpected status: code=%d body=%s", response.Code, response.Body.String())
	}

	request = authenticatedRequest(http.MethodPost, "/api/v1/ssh/disable")
	response = httptest.NewRecorder()
	router.ServeHTTP(response, request)
	if response.Code != http.StatusOK || sshManager.enabled {
		t.Fatalf("disable failed: code=%d body=%s enabled=%v", response.Code, response.Body.String(), sshManager.enabled)
	}
}

func TestTimezoneStatusAndSet(t *testing.T) {
	manager := &fakeTimezone{value: "Etc/UTC"}
	router := NewRouter(Dependencies{Token: "secret-token", Timezone: manager})

	request := authenticatedRequest(http.MethodGet, "/api/v1/timezone")
	response := httptest.NewRecorder()
	router.ServeHTTP(response, request)
	if response.Code != http.StatusOK || response.Body.String() != "{\"timezone\":\"Etc/UTC\"}\n" {
		t.Fatalf("unexpected status: code=%d body=%s", response.Code, response.Body.String())
	}

	request = httptest.NewRequest(http.MethodPut, "/api/v1/timezone", strings.NewReader(`{"timezone":"Europe/Vienna"}`))
	request.Header.Set("Authorization", "Bearer secret-token")
	request.Header.Set("Content-Type", "application/json")
	response = httptest.NewRecorder()
	router.ServeHTTP(response, request)
	if response.Code != http.StatusOK || manager.value != "Europe/Vienna" || response.Body.String() != "{\"timezone\":\"Europe/Vienna\"}\n" {
		t.Fatalf("set failed: code=%d body=%s timezone=%q", response.Code, response.Body.String(), manager.value)
	}
}

func TestRunningContainersReturnsCurrentVersions(t *testing.T) {
	router := NewRouter(Dependencies{
		Token:   "secret-token",
		Update:  &fakeUpdate{},
		Monitor: fakeMonitor{},
		Containers: fakeContainers{items: []health.Container{
			{Name: "atlas-api", Image: "ghcr.io/gametec-live/atlas-api:latest", ImageID: "sha256:api", Version: "2.4.1"},
		}},
	})
	request := authenticatedRequest(http.MethodGet, "/api/v1/containers")
	response := httptest.NewRecorder()
	router.ServeHTTP(response, request)

	if response.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d: %s", response.Code, response.Body.String())
	}
	var body struct {
		Items []health.Container `json:"items"`
		Count int                `json:"count"`
	}
	if err := json.Unmarshal(response.Body.Bytes(), &body); err != nil {
		t.Fatal(err)
	}
	if body.Count != 1 || len(body.Items) != 1 || body.Items[0].Version != "2.4.1" || body.Items[0].ImageID != "sha256:api" {
		t.Fatalf("unexpected container response: %#v", body)
	}
}

func TestRemoteAccessCanBeProvisionedAndGathered(t *testing.T) {
	manager := &fakeRemoteAccess{}
	router := NewRouter(Dependencies{Token: "secret-token", RemoteAccess: manager})

	body := bytes.NewBufferString(`{"authKey":"tskey-auth-test","hostname":"atlas-1"}`)
	request := httptest.NewRequest(http.MethodPut, "/api/v1/connections/remote-access/tailscale", body)
	request.Header.Set("Authorization", "Bearer secret-token")
	request.Header.Set("Content-Type", "application/json")
	response := httptest.NewRecorder()
	router.ServeHTTP(response, request)
	if response.Code != http.StatusOK || !manager.status.Tailscale.Provisioned {
		t.Fatalf("provision failed: code=%d body=%s", response.Code, response.Body.String())
	}

	request = authenticatedRequest(http.MethodGet, "/api/v1/connections/remote-access")
	response = httptest.NewRecorder()
	router.ServeHTTP(response, request)
	if response.Code != http.StatusOK || !strings.Contains(response.Body.String(), `"tailscale":{"provisioned":true,"state":"active","detail":"running"}`) {
		t.Fatalf("unexpected status: code=%d body=%s", response.Code, response.Body.String())
	}
}

func authenticatedRequest(method, path string) *http.Request {
	request := httptest.NewRequest(method, path, nil)
	request.Header.Set("Authorization", "Bearer secret-token")
	return request
}

func TestEveryRouteRequiresBearerToken(t *testing.T) {
	router := testRouter(t.TempDir(), &fakeUpdate{}, &fakePower{})
	request := httptest.NewRequest(http.MethodGet, "/healthz", nil)
	response := httptest.NewRecorder()
	router.ServeHTTP(response, request)
	if response.Code != http.StatusUnauthorized {
		t.Fatalf("expected 401, got %d", response.Code)
	}

	request = httptest.NewRequest(http.MethodGet, "/healthz", nil)
	request.Header.Set("Authorization", "Bearer secret-token")
	response = httptest.NewRecorder()
	router.ServeHTTP(response, request)
	if response.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d", response.Code)
	}
}

func TestUpdateStreamsMultipartBundleThenSchedulesTryboot(t *testing.T) {
	stateDir := t.TempDir()
	updateManager := &fakeUpdate{}
	powerManager := &fakePower{}
	router := testRouter(stateDir, updateManager, powerManager)

	var body bytes.Buffer
	writer := multipart.NewWriter(&body)
	part, err := writer.CreateFormFile("bundle", "atlas-update.tar.zst")
	if err != nil {
		t.Fatal(err)
	}
	if _, err := part.Write([]byte("signed update payload")); err != nil {
		t.Fatal(err)
	}
	if err := writer.Close(); err != nil {
		t.Fatal(err)
	}
	request := httptest.NewRequest(http.MethodPost, "/api/v1/update", &body)
	request.Header.Set("Authorization", "Bearer secret-token")
	request.Header.Set("Content-Type", writer.FormDataContentType())
	response := httptest.NewRecorder()
	router.ServeHTTP(response, request)

	if response.Code != http.StatusAccepted {
		t.Fatalf("expected 202, got %d: %s", response.Code, response.Body.String())
	}
	if string(updateManager.applied) != "signed update payload" || !powerManager.tryboot {
		t.Fatalf("update was not applied and rebooted: payload=%q tryboot=%v", updateManager.applied, powerManager.tryboot)
	}
	files, err := filepath.Glob(filepath.Join(stateDir, "update-upload.*.tar.zst"))
	if err != nil || len(files) != 0 {
		t.Fatalf("staged upload was not removed: files=%#v error=%v", files, err)
	}
}
