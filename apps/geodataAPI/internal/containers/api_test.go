package containers

import (
	"context"
	"errors"
	"net/http"
	"net/http/httptest"
	"testing"
)

type testRestarter struct {
	pingCalls int
	calls     int
	pingErr   error
	err       error
}

func (restarter *testRestarter) Ping(context.Context) error {
	restarter.pingCalls++
	return restarter.pingErr
}

func (restarter *testRestarter) Restart(context.Context) error {
	restarter.calls++
	return restarter.err
}

func TestAPIHealthChecksContainerRuntime(t *testing.T) {
	restarter := &testRestarter{}
	request := httptest.NewRequest(http.MethodGet, "/healthz", nil)
	response := httptest.NewRecorder()

	NewAPI(restarter).ServeHTTP(response, request)

	if response.Code != http.StatusNoContent || restarter.pingCalls != 1 {
		t.Fatalf("status = %d, ping calls = %d", response.Code, restarter.pingCalls)
	}
}

func TestAPIHealthFailsWhenContainerRuntimeIsUnavailable(t *testing.T) {
	restarter := &testRestarter{pingErr: errors.New("unavailable")}
	request := httptest.NewRequest(http.MethodGet, "/healthz", nil)
	response := httptest.NewRecorder()

	NewAPI(restarter).ServeHTTP(response, request)

	if response.Code != http.StatusServiceUnavailable {
		t.Fatalf("status = %d", response.Code)
	}
}

func TestAPIRestartsConsumers(t *testing.T) {
	restarter := &testRestarter{}
	request := httptest.NewRequest(http.MethodPost, "/restart", nil)
	response := httptest.NewRecorder()

	NewAPI(restarter).ServeHTTP(response, request)

	if response.Code != http.StatusNoContent || restarter.calls != 1 {
		t.Fatalf("status = %d, restart calls = %d", response.Code, restarter.calls)
	}
}

func TestAPIDoesNotExposeArbitraryContainerOperations(t *testing.T) {
	restarter := &testRestarter{}
	request := httptest.NewRequest(http.MethodPost, "/containers/router-id/exec", nil)
	response := httptest.NewRecorder()

	NewAPI(restarter).ServeHTTP(response, request)

	if response.Code != http.StatusNotFound || restarter.calls != 0 {
		t.Fatalf("status = %d, restart calls = %d", response.Code, restarter.calls)
	}
}

func TestAPIReturnsFailureWithoutDetails(t *testing.T) {
	restarter := &testRestarter{err: errors.New("sensitive runtime detail")}
	request := httptest.NewRequest(http.MethodPost, "/restart", nil)
	response := httptest.NewRecorder()

	NewAPI(restarter).ServeHTTP(response, request)

	if response.Code != http.StatusInternalServerError || response.Body.String() != "restart failed\n" {
		t.Fatalf("response = %d %q", response.Code, response.Body.String())
	}
}
