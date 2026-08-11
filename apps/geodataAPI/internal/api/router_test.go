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

type fakeRunner struct{}

func (fakeRunner) Run(_ context.Context, name string, args ...string) error {
	var output string
	for index, argument := range args {
		if argument == "--output" && index+1 < len(args) {
			output = args[index+1]
		}
		if strings.HasPrefix(argument, "--output=") {
			output = strings.TrimPrefix(argument, "--output=")
		}
	}
	if output == "" {
		return nil
	}
	return os.WriteFile(output, []byte(name+" output"), 0o644)
}

func TestDownloadListWebsocketAndDelete(t *testing.T) {
	pbf := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, request *http.Request) {
		w.Header().Set("ETag", `"test-v1"`)
		if request.Header.Get("If-None-Match") == `"test-v1"` {
			w.WriteHeader(http.StatusNotModified)
			return
		}
		w.Header().Set("Content-Length", "8")
		_, _ = w.Write([]byte("test-pbf"))
	}))
	defer pbf.Close()

	dataDir := t.TempDir()
	dataStore, err := store.Open(dataDir)
	if err != nil {
		t.Fatal(err)
	}
	cfg := config.Config{DataDir: dataDir, HTTPTimeout: time.Second, OsmiumBinary: "osmium", PackgenBinary: "packgen", JavaBinary: "java", PlanetilerJar: "planetiler.jar"}
	regionCatalog := fakeCatalog{region: model.Region{
		ID: "austria", Name: "Austria", PBFURL: pbf.URL, CountryCodes: []string{"AT"},
		SizeBytes: &model.EstimatedDatasetSize{PBF: 8, GeocoderEstimate: 24, MapEstimate: 12, TotalEstimate: 44},
	}}
	manager := jobs.NewManager(cfg, dataStore, regionCatalog, fakeRunner{})
	manager.Start()
	defer manager.Stop()
	server := httptest.NewServer(api.NewRouter(manager, regionCatalog, dataStore))
	defer server.Close()

	response := requestJSON(t, http.MethodGet, server.URL+"/api/v1/catalog", "")
	var available struct {
		Items     []model.Region   `json:"items"`
		Count     int              `json:"count"`
		DiskSpace *model.DiskSpace `json:"disk_space"`
	}
	decode(t, response, &available)
	if available.Count != 1 || available.Items[0].SizeBytes == nil || available.Items[0].SizeBytes.TotalEstimate != 44 {
		t.Fatalf("catalog sizes missing: %#v", available)
	}
	if available.DiskSpace == nil || available.DiskSpace.FreeBytes == 0 {
		t.Fatalf("catalog disk space missing: %#v", available.DiskSpace)
	}

	response = requestJSON(t, http.MethodPost, server.URL+"/api/v1/datasets", `{"id":"austria","excludeRoads":true}`)
	if response.StatusCode != http.StatusAccepted {
		t.Fatalf("start status: %d %s", response.StatusCode, readBody(response))
	}
	var started model.Job
	decode(t, response, &started)
	completed := waitForJob(t, manager, started.ID)
	if completed.State != model.JobCompleted {
		t.Fatalf("job failed: %#v", completed)
	}
	if !completed.Request.ExcludeRoads {
		t.Fatal("excludeRoads was not retained in the job request")
	}

	content, err := os.ReadFile(filepath.Join(dataDir, "austria.osm.pbf"))
	if err != nil || string(content) != "test-pbf" {
		t.Fatalf("unexpected PBF %q, %v", content, err)
	}
	for _, required := range []string{"austria.sqlite", "map.pmtiles"} {
		if _, err := os.Stat(filepath.Join(dataDir, required)); err != nil {
			t.Fatalf("missing synchronized artifact %q: %v", required, err)
		}
	}
	for _, unwanted := range []string{"pbf", "geocoder"} {
		if _, err := os.Stat(filepath.Join(dataDir, unwanted)); !os.IsNotExist(err) {
			t.Fatalf("unexpected data subdirectory %q: %v", unwanted, err)
		}
	}
	response = requestJSON(t, http.MethodGet, server.URL+"/api/v1/datasets", "")
	var installed struct {
		Items     []model.Dataset  `json:"items"`
		Count     int              `json:"count"`
		DiskSpace *model.DiskSpace `json:"disk_space"`
	}
	decode(t, response, &installed)
	if installed.Count != 1 {
		t.Fatalf("installed count = %d", installed.Count)
	}
	if len(installed.Items) != 1 || len(installed.Items[0].Artifacts) != 3 {
		t.Fatalf("dataset is not synchronized across all formats: %#v", installed.Items)
	}
	if !installed.Items[0].ExcludeRoads {
		t.Fatal("installed dataset does not report excluded roads")
	}
	if installed.DiskSpace == nil || installed.DiskSpace.FreeBytes == 0 {
		t.Fatalf("dataset disk space missing: %#v", installed.DiskSpace)
	}
	response = requestJSON(t, http.MethodPost, server.URL+"/api/v1/datasets/austria/update", "")
	if response.StatusCode != http.StatusAccepted {
		t.Fatalf("update status: %d %s", response.StatusCode, readBody(response))
	}
	var update model.Job
	decode(t, response, &update)
	if job := waitForJob(t, manager, update.ID); job.State != model.JobCompleted || job.Stage != "up_to_date" {
		t.Fatalf("unchanged update failed: %#v", job)
	}

	wsURL, _ := url.Parse(server.URL)
	wsURL.Scheme = "ws"
	wsURL.Path = "/api/v1/jobs/ws"
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

	response = requestJSON(t, http.MethodDelete, server.URL+"/api/v1/datasets/austria", "")
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

	customURL := pbf.URL + "/custom-latest.osm.pbf?token=test"
	response = requestJSON(t, http.MethodPost, server.URL+"/api/v1/datasets", `{"url":"`+customURL+`","excludeRoads":true}`)
	if response.StatusCode != http.StatusAccepted {
		t.Fatalf("custom URL install status: %d %s", response.StatusCode, readBody(response))
	}
	var customInstall model.Job
	decode(t, response, &customInstall)
	if job := waitForJob(t, manager, customInstall.ID); job.State != model.JobCompleted {
		t.Fatalf("custom URL install failed: %#v", job)
	}
	custom, found := dataStore.Dataset("custom")
	if !found || custom.SourceType != "url" || custom.SourceURL != customURL || custom.SourceRegion != "" {
		t.Fatalf("custom URL source was not retained: %#v", custom)
	}
	response = requestJSON(t, http.MethodPost, server.URL+"/api/v1/datasets/custom/update", "")
	if response.StatusCode != http.StatusAccepted {
		t.Fatalf("custom URL update status: %d %s", response.StatusCode, readBody(response))
	}
	var customUpdate model.Job
	decode(t, response, &customUpdate)
	if job := waitForJob(t, manager, customUpdate.ID); job.State != model.JobCompleted || job.Stage != "up_to_date" {
		t.Fatalf("custom URL update failed: %#v", job)
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
	server := httptest.NewServer(api.NewRouter(manager, regionCatalog, dataStore))
	defer server.Close()

	response := requestJSON(t, http.MethodPost, server.URL+"/api/v1/datasets", `{"bbox":{"minLongitude":17,"minLatitude":49,"maxLongitude":16,"maxLatitude":48}}`)
	if response.StatusCode != http.StatusBadRequest {
		t.Fatalf("status = %d, body = %s", response.StatusCode, readBody(response))
	}
	response = requestJSON(t, http.MethodPost, server.URL+"/api/v1/datasets", `{"id":"world","url":"https://example.invalid/world.osm.pbf"}`)
	if response.StatusCode != http.StatusBadRequest {
		t.Fatalf("multiple source status = %d, body = %s", response.StatusCode, readBody(response))
	}
	response = requestJSON(t, http.MethodPost, server.URL+"/api/v1/datasets", `{"url":"file:///tmp/world.osm.pbf"}`)
	if response.StatusCode != http.StatusBadRequest {
		t.Fatalf("invalid URL status = %d, body = %s", response.StatusCode, readBody(response))
	}
	response = requestJSON(t, http.MethodPost, server.URL+"/api/v1/datasets/missing/update", "")
	if response.StatusCode != http.StatusNotFound {
		t.Fatalf("missing update status = %d, body = %s", response.StatusCode, readBody(response))
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
