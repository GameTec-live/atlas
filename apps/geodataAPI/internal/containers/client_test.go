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
	var requests []string
	client.http.Transport = roundTripFunc(func(request *http.Request) (*http.Response, error) {
		requests = append(requests, request.Method+" "+request.URL.Path)
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
		if request.Method == http.MethodPost && request.URL.Path == "/containers/router-id/exec" {
			var command struct {
				Cmd []string `json:"Cmd"`
			}
			if err := json.NewDecoder(request.Body).Decode(&command); err != nil {
				t.Fatal(err)
			}
			want := []string{
				"rm", "-rf",
				"/custom_files/valhalla_tiles.tar",
				"/custom_files/valhalla_tiles",
			}
			if !reflect.DeepEqual(command.Cmd, want) {
				t.Fatalf("cleanup command = %#v, want %#v", command.Cmd, want)
			}
			return response(http.StatusCreated, `{"Id":"cleanup-id"}`), nil
		}
		if request.Method == http.MethodPost && request.URL.Path == "/exec/cleanup-id/start" {
			return response(http.StatusOK, ""), nil
		}
		if request.Method == http.MethodGet && request.URL.Path == "/exec/cleanup-id/json" {
			return response(http.StatusOK, `{"Running":false,"ExitCode":0}`), nil
		}
		if request.Method == http.MethodPost && strings.HasSuffix(request.URL.Path, "/restart") {
			return response(http.StatusNoContent, ""), nil
		}
		t.Fatalf("unexpected runtime request: %s %s", request.Method, request.URL.String())
		return nil, nil
	})

	if err := client.Restart(context.Background()); err != nil {
		t.Fatal(err)
	}
	want := []string{
		"GET /containers/json",
		"POST /containers/router-id/exec",
		"POST /exec/cleanup-id/start",
		"GET /exec/cleanup-id/json",
		"POST /containers/router-id/restart",
		"POST /containers/geocoder-id/restart",
	}
	if !reflect.DeepEqual(requests, want) {
		t.Fatalf("runtime requests = %#v, want %#v", requests, want)
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
