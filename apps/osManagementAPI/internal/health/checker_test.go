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
