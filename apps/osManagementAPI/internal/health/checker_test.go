package health

import (
	"context"
	"io"
	"net/http"
	"strings"
	"testing"
)

type roundTripFunc func(*http.Request) (*http.Response, error)

func (function roundTripFunc) RoundTrip(request *http.Request) (*http.Response, error) {
	return function(request)
}

func response(status int, body string) *http.Response {
	return &http.Response{StatusCode: status, Body: io.NopCloser(strings.NewReader(body)), Header: make(http.Header)}
}

func TestHealthyRequiresEveryContainerAndAPI(t *testing.T) {
	checker := &Checker{
		podmanClient: &http.Client{Transport: roundTripFunc(func(request *http.Request) (*http.Response, error) {
			if strings.Contains(request.URL.Path, "/atlas-api/") {
				return response(http.StatusOK, `{"State":{"Status":"running"}}`), nil
			}
			return response(http.StatusOK, `{"State":{"Status":"running","Health":{"Status":"healthy"}}}`), nil
		})},
		healthClient: &http.Client{Transport: roundTripFunc(func(*http.Request) (*http.Response, error) {
			return response(http.StatusOK, "ok"), nil
		})},
		healthURL: "https://atlas.test/health",
	}
	healthy, detail := checker.Healthy(context.Background())
	if !healthy {
		t.Fatalf("expected healthy system: %s", detail)
	}
}

func TestUnhealthyContainerFailsCheck(t *testing.T) {
	checker := &Checker{
		podmanClient: &http.Client{Transport: roundTripFunc(func(request *http.Request) (*http.Response, error) {
			if strings.Contains(request.URL.Path, "/atlas-map/") {
				return response(http.StatusOK, `{"State":{"Status":"running","Health":{"Status":"unhealthy"}}}`), nil
			}
			return response(http.StatusOK, `{"State":{"Status":"running","Health":{"Status":"healthy"}}}`), nil
		})},
		healthClient: &http.Client{Transport: roundTripFunc(func(*http.Request) (*http.Response, error) {
			return response(http.StatusOK, "ok"), nil
		})},
		healthURL: "https://atlas.test/health",
	}
	healthy, detail := checker.Healthy(context.Background())
	if healthy || !strings.Contains(detail, "atlas-map health is unhealthy") {
		t.Fatalf("expected map health failure, got healthy=%v detail=%q", healthy, detail)
	}
}

func TestRunningContainersReturnsVersionLabelsAndImageIDFallback(t *testing.T) {
	checker := &Checker{podmanClient: &http.Client{Transport: roundTripFunc(func(request *http.Request) (*http.Response, error) {
		if request.URL.Path != "/v1.41/containers/json" || request.URL.Query().Get("all") != "false" {
			t.Fatalf("unexpected Podman request: %s", request.URL.String())
		}
		return response(http.StatusOK, `[
			{"Id":"web-id","Names":["/atlas-web"],"Image":"atlas-web:latest","ImageID":"sha256:web","State":"running","Labels":{"org.opencontainers.image.version":"3.2.0"}},
			{"Id":"db-id","Names":["atlas-db"],"Image":"postgres:18-alpine","ImageID":"sha256:db","State":"running","Labels":{}},
			{"Id":"old-id","Names":["old"],"Image":"old:latest","ImageID":"sha256:old","State":"exited","Labels":{}}
		]`), nil
	})}}

	items, err := checker.RunningContainers(context.Background())
	if err != nil {
		t.Fatal(err)
	}
	if len(items) != 2 {
		t.Fatalf("expected two running containers, got %#v", items)
	}
	if items[0].Name != "atlas-db" || items[0].Version != "sha256:db" {
		t.Fatalf("expected image ID fallback, got %#v", items[0])
	}
	if items[1].Name != "atlas-web" || items[1].Version != "3.2.0" || items[1].ImageID != "sha256:web" {
		t.Fatalf("expected OCI version label, got %#v", items[1])
	}
}
