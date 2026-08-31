package api

import (
	"bytes"
	"context"
	"mime/multipart"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"testing"
	"time"

	"github.com/GameTec-live/atlas/apps/osManagementAPI/internal/networkmanager"
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

type fakePower struct {
	tryboot bool
}

func (*fakePower) Reboot(context.Context) error          { return nil }
func (p *fakePower) RebootTryboot(context.Context) error { p.tryboot = true; return nil }
func (*fakePower) Poweroff(context.Context) error        { return nil }

type fakeReset struct{}

func (fakeReset) Request() error { return nil }

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

type immediateScheduler struct{}

func (immediateScheduler) After(_ time.Duration, operation func()) { operation() }

func testRouter(stateDir string, updateManager *fakeUpdate, powerManager *fakePower) http.Handler {
	return NewRouter(Dependencies{
		Token:          "secret-token",
		StateDir:       stateDir,
		MaxUpdateBytes: 1024,
		Update:         updateManager,
		Monitor:        fakeMonitor{},
		Power:          powerManager,
		Reset:          fakeReset{},
		Network:        fakeNetwork{},
		Origins:        fakeOrigins{},
		Scheduler:      immediateScheduler{},
	})
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
