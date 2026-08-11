package api_test

import (
	"bytes"
	"context"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"net/url"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/GameTec-live/atlas/apps/geodataAPI/internal/api"
	"github.com/GameTec-live/atlas/apps/geodataAPI/internal/config"
	"github.com/GameTec-live/atlas/apps/geodataAPI/internal/jobs"
	"github.com/GameTec-live/atlas/apps/geodataAPI/internal/model"
	"github.com/GameTec-live/atlas/apps/geodataAPI/internal/store"
	"github.com/gorilla/websocket"
)

type fakeCatalog struct{ region model.Region }

func (f fakeCatalog) List(context.Context, string, string) ([]model.Region, error) {
	return []model.Region{f.region}, nil
}
func (f fakeCatalog) Find(context.Context, string) (model.Region, error) { return f.region, nil }
func (f fakeCatalog) Covering(context.Context, model.Bounds) (model.Region, error) {
	return f.region, nil
}

func TestDownloadListWebsocketAndDelete(t *testing.T) {
	pbf := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.Header().Set("Content-Length", "8")
		_, _ = w.Write([]byte("test-pbf"))
	}))
	defer pbf.Close()

	dataDir := t.TempDir()
	dataStore, err := store.Open(dataDir)
	if err != nil {
		t.Fatal(err)
	}
	cfg := config.Config{DataDir: dataDir, HTTPTimeout: time.Second, OsmiumBinary: "osmium", PackgenBinary: "packgen", JavaBinary: "java"}
	regionCatalog := fakeCatalog{region: model.Region{ID: "austria", Name: "Austria", PBFURL: pbf.URL, CountryCodes: []string{"AT"}}}
	manager := jobs.NewManager(cfg, dataStore, regionCatalog, nil)
	manager.Start()
	defer manager.Stop()
	server := httptest.NewServer(api.NewRouter(manager, regionCatalog, dataStore, cfg))
	defer server.Close()

	response := requestJSON(t, http.MethodPost, server.URL+"/api/v1/downloads/name", `{"name":"austria","products":["pbf"]}`)
	if response.StatusCode != http.StatusAccepted {
		t.Fatalf("start status: %d %s", response.StatusCode, readBody(response))
	}
	var started model.Job
	decode(t, response, &started)
	completed := waitForJob(t, manager, started.ID)
	if completed.State != model.JobCompleted {
		t.Fatalf("job failed: %#v", completed)
	}

	content, err := os.ReadFile(filepath.Join(dataDir, "austria.osm.pbf"))
	if err != nil || string(content) != "test-pbf" {
		t.Fatalf("unexpected PBF %q, %v", content, err)
	}
	for _, unwanted := range []string{"pbf", "geocoder"} {
		if _, err := os.Stat(filepath.Join(dataDir, unwanted)); !os.IsNotExist(err) {
			t.Fatalf("unexpected data subdirectory %q: %v", unwanted, err)
		}
	}
	response = requestJSON(t, http.MethodGet, server.URL+"/api/v1/installed", "")
	var installed struct {
		Count int `json:"count"`
	}
	decode(t, response, &installed)
	if installed.Count != 1 {
		t.Fatalf("installed count = %d", installed.Count)
	}

	wsURL, _ := url.Parse(server.URL)
	wsURL.Scheme = "ws"
	wsURL.Path = "/api/v1/downloads/ws"
	connection, _, err := websocket.DefaultDialer.Dial(wsURL.String(), nil)
	if err != nil {
		t.Fatal(err)
	}
	var snapshot struct {
		Type string      `json:"type"`
		Jobs []model.Job `json:"jobs"`
	}
	if err := connection.ReadJSON(&snapshot); err != nil {
		t.Fatal(err)
	}
	_ = connection.Close()
	if snapshot.Type != "snapshot" || len(snapshot.Jobs) == 0 {
		t.Fatalf("unexpected websocket snapshot: %#v", snapshot)
	}

	response = requestJSON(t, http.MethodDelete, server.URL+"/api/v1/data/austria", "")
	if response.StatusCode != http.StatusAccepted {
		t.Fatalf("delete status: %d %s", response.StatusCode, readBody(response))
	}
	var deletion model.Job
	decode(t, response, &deletion)
	if job := waitForJob(t, manager, deletion.ID); job.State != model.JobCompleted {
		t.Fatalf("delete failed: %#v", job)
	}
	if _, err := os.Stat(filepath.Join(dataDir, "austria.osm.pbf")); !os.IsNotExist(err) {
		t.Fatalf("PBF still exists: %v", err)
	}
}

func TestBBoxValidation(t *testing.T) {
	dataDir := t.TempDir()
	dataStore, err := store.Open(dataDir)
	if err != nil {
		t.Fatal(err)
	}
	cfg := config.Config{DataDir: dataDir, HTTPTimeout: time.Second}
	regionCatalog := fakeCatalog{region: model.Region{ID: "world", Name: "World", PBFURL: "https://example.invalid/world.pbf"}}
	manager := jobs.NewManager(cfg, dataStore, regionCatalog, nil)
	manager.Start()
	defer manager.Stop()
	server := httptest.NewServer(api.NewRouter(manager, regionCatalog, dataStore, cfg))
	defer server.Close()

	response := requestJSON(t, http.MethodPost, server.URL+"/api/v1/downloads/bbox", `{"bbox":{"west":17,"south":49,"east":16,"north":48},"products":["pbf"]}`)
	if response.StatusCode != http.StatusBadRequest {
		t.Fatalf("status = %d, body = %s", response.StatusCode, readBody(response))
	}
}

func requestJSON(t *testing.T, method, endpoint, body string) *http.Response {
	t.Helper()
	var reader io.Reader
	if body != "" {
		reader = bytes.NewBufferString(body)
	}
	request, err := http.NewRequest(method, endpoint, reader)
	if err != nil {
		t.Fatal(err)
	}
	if body != "" {
		request.Header.Set("Content-Type", "application/json")
	}
	response, err := http.DefaultClient.Do(request)
	if err != nil {
		t.Fatal(err)
	}
	return response
}

func decode(t *testing.T, response *http.Response, destination any) {
	t.Helper()
	defer response.Body.Close()
	if err := json.NewDecoder(response.Body).Decode(destination); err != nil {
		t.Fatal(err)
	}
}

func readBody(response *http.Response) string {
	defer response.Body.Close()
	content, _ := io.ReadAll(response.Body)
	return strings.TrimSpace(string(content))
}

func waitForJob(t *testing.T, manager *jobs.Manager, id string) model.Job {
	t.Helper()
	deadline := time.Now().Add(3 * time.Second)
	for time.Now().Before(deadline) {
		job, ok := manager.Job(id)
		if ok && job.State != model.JobQueued && job.State != model.JobRunning {
			return job
		}
		time.Sleep(10 * time.Millisecond)
	}
	t.Fatalf("job %s did not finish", id)
	return model.Job{}
}
