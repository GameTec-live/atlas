package containers

import (
	"context"
	"encoding/json"
	"io"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"reflect"
	"sort"
	"strings"
	"testing"
	"time"
)

type roundTripFunc func(*http.Request) (*http.Response, error)

func (function roundTripFunc) RoundTrip(request *http.Request) (*http.Response, error) {
	return function(request)
}

func TestRestartFindsLabeledConsumers(t *testing.T) {
	socketPath := filepath.Join(t.TempDir(), "docker.sock")
	if err := os.WriteFile(socketPath, nil, 0o600); err != nil {
		t.Fatal(err)
	}

	client := NewClient(socketPath, time.Second)
	var restarted []string
	client.http.Transport = roundTripFunc(func(request *http.Request) (*http.Response, error) {
		if request.Method == http.MethodGet && request.URL.Path == "/containers/json" {
			filters, err := url.QueryUnescape(request.URL.Query().Get("filters"))
			if err != nil {
				t.Fatal(err)
			}
			var decoded map[string][]string
			if err := json.Unmarshal([]byte(filters), &decoded); err != nil {
				t.Fatal(err)
			}
			if !reflect.DeepEqual(decoded["label"], []string{consumerLabel}) {
				t.Fatalf("unexpected filters: %#v", decoded)
			}
			return response(http.StatusOK, `[
				{"Id":"router-id","Names":["/atlas-router-1"],"Labels":{"live.gametec.atlas.geodata-consumer":"router"}},
				{"Id":"geocoder-id","Names":["/atlas-geocoder-1"],"Labels":{"live.gametec.atlas.geodata-consumer":"geocoder"}},
				{"Id":"map-id","Names":["/atlas-map-1"],"Labels":{}}
			]`), nil
		}
		if request.Method == http.MethodPost {
			restarted = append(restarted, request.URL.Path)
			return response(http.StatusNoContent, ""), nil
		}
		t.Fatalf("unexpected runtime request: %s %s", request.Method, request.URL.String())
		return nil, nil
	})

	if err := client.Restart(context.Background()); err != nil {
		t.Fatal(err)
	}
	sort.Strings(restarted)
	want := []string{"/containers/geocoder-id/restart", "/containers/router-id/restart"}
	if !reflect.DeepEqual(restarted, want) {
		t.Fatalf("restart requests = %#v, want %#v", restarted, want)
	}
}

func TestRestartIsDisabledWhenSocketIsAbsent(t *testing.T) {
	client := NewClient(filepath.Join(t.TempDir(), "missing.sock"), time.Second)
	client.http.Transport = roundTripFunc(func(request *http.Request) (*http.Response, error) {
		t.Fatalf("unexpected runtime request: %s", request.URL)
		return nil, nil
	})
	if err := client.Restart(context.Background()); err != nil {
		t.Fatal(err)
	}
}

func response(status int, body string) *http.Response {
	return &http.Response{
		StatusCode: status,
		Header:     make(http.Header),
		Body:       io.NopCloser(strings.NewReader(body)),
	}
}
