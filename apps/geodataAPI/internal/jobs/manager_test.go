package jobs

import (
	"context"
	"fmt"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/GameTec-live/atlas/apps/geodataAPI/internal/config"
	"github.com/GameTec-live/atlas/apps/geodataAPI/internal/model"
	"github.com/GameTec-live/atlas/apps/geodataAPI/internal/store"
)

type testCatalog struct{ regions map[string]model.Region }

func (c testCatalog) List(context.Context, string, string) ([]model.Region, error) { return nil, nil }
func (c testCatalog) Find(_ context.Context, name string) (model.Region, error) {
	region, ok := c.regions[name]
	if !ok {
		return model.Region{}, fmt.Errorf("not found")
	}
	return region, nil
}
func (c testCatalog) Covering(context.Context, model.Bounds) (model.Region, error) {
	return c.regions["one"], nil
}

type testRunner struct {
	mu    sync.Mutex
	calls []string
}

func (r *testRunner) Run(_ context.Context, name string, args ...string) error {
	r.mu.Lock()
	r.calls = append(r.calls, name+" "+strings.Join(args, " "))
	r.mu.Unlock()

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
		return fmt.Errorf("test command has no output argument")
	}
	return os.WriteFile(output, []byte(name+" output"), 0o644)
}

func TestFullPipelineMergesAndRebuildsMap(t *testing.T) {
	pbf := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, request *http.Request) {
		_, _ = w.Write([]byte("pbf-" + request.URL.Path))
	}))
	defer pbf.Close()

	root := t.TempDir()
	dataStore, err := store.Open(root)
	if err != nil {
		t.Fatal(err)
	}
	regionCatalog := testCatalog{regions: map[string]model.Region{
		"one": {ID: "one", Name: "One", PBFURL: pbf.URL + "/one", CountryCodes: []string{"AT"}},
		"two": {ID: "two", Name: "Two", PBFURL: pbf.URL + "/two", CountryCodes: []string{"DE"}},
	}}
	runner := &testRunner{}
	cfg := config.Config{DataDir: root, HTTPTimeout: time.Second, OsmiumBinary: "osmium", PackgenBinary: "packgen", JavaBinary: "java", PlanetilerJar: "planetiler.jar"}
	manager := NewManager(cfg, dataStore, regionCatalog, runner)
	manager.Start()
	defer manager.Stop()

	first, err := manager.StartByName(context.Background(), "one", nil)
	if err != nil {
		t.Fatal(err)
	}
	if job := waitJob(t, manager, first.ID); job.State != model.JobCompleted {
		t.Fatalf("first job: %#v", job)
	}
	for _, path := range []string{"one.osm.pbf", "one.sqlite", "map.pmtiles"} {
		if _, err := os.Stat(filepath.Join(root, filepath.FromSlash(path))); err != nil {
			t.Fatalf("missing %s: %v", path, err)
		}
	}

	second, err := manager.StartByName(context.Background(), "two", nil)
	if err != nil {
		t.Fatal(err)
	}
	if job := waitJob(t, manager, second.ID); job.State != model.JobCompleted {
		t.Fatalf("second job: %#v", job)
	}
	runner.mu.Lock()
	calls := strings.Join(runner.calls, "\n")
	runner.mu.Unlock()
	if !strings.Contains(calls, "osmium merge") {
		t.Fatalf("second install did not merge PBFs:\n%s", calls)
	}

	deletion, err := manager.Delete("one")
	if err != nil {
		t.Fatal(err)
	}
	if job := waitJob(t, manager, deletion.ID); job.State != model.JobCompleted {
		t.Fatalf("delete job: %#v", job)
	}
	if _, found := dataStore.Dataset("one"); found {
		t.Fatal("deleted dataset remains in manifest")
	}
	if _, err := os.Stat(filepath.Join(root, "map.pmtiles")); err != nil {
		t.Fatalf("map was not rebuilt: %v", err)
	}
}

func waitJob(t *testing.T, manager *Manager, id string) model.Job {
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
