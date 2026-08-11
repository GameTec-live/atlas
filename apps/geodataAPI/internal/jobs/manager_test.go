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
	mu       sync.Mutex
	calls    []string
	failJava bool
}

type testReloader struct {
	mu    sync.Mutex
	calls int
}

func (r *testReloader) Restart(context.Context) error {
	r.mu.Lock()
	r.calls++
	r.mu.Unlock()
	return nil
}

func (r *testRunner) Run(_ context.Context, name string, args ...string) error {
	r.mu.Lock()
	r.calls = append(r.calls, name+" "+strings.Join(args, " "))
	fail := r.failJava && name == "java"
	r.mu.Unlock()
	if fail {
		return fmt.Errorf("test map build failed")
	}

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

func (r *testRunner) setFailJava(fail bool) {
	r.mu.Lock()
	r.failJava = fail
	r.mu.Unlock()
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

	first, err := manager.Install(context.Background(), "one", "", nil, true)
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

	second, err := manager.Install(context.Background(), "two", "", nil, false)
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
	if !strings.Contains(calls, "packgen build --source openstreetmap") || !strings.Contains(calls, "--include-roads=false") {
		t.Fatalf("first install did not exclude roads from its geocoder pack:\n%s", calls)
	}
	installed, found := dataStore.Dataset("one")
	if !found || !installed.ExcludeRoads {
		t.Fatalf("exclude-roads setting was not persisted: %#v", installed)
	}

	runner.setFailJava(true)
	failedDeletion, err := manager.Delete("one")
	if err != nil {
		t.Fatal(err)
	}
	if job := waitJob(t, manager, failedDeletion.ID); job.State != model.JobFailed {
		t.Fatalf("deletion should fail before removing synchronized data: %#v", job)
	}
	if _, found := dataStore.Dataset("one"); !found {
		t.Fatal("failed map rebuild removed the dataset")
	}
	for _, path := range []string{"one.osm.pbf", "one.sqlite", "map.pmtiles"} {
		if _, err := os.Stat(filepath.Join(root, path)); err != nil {
			t.Fatalf("failed map rebuild removed %s: %v", path, err)
		}
	}
	runner.setFailJava(false)

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

func TestReloadWaitsUntilNoOtherJobsAreActive(t *testing.T) {
	dataStore, err := store.Open(t.TempDir())
	if err != nil {
		t.Fatal(err)
	}
	reloader := &testReloader{}
	manager := NewManager(config.Config{ReloadTimeout: time.Second}, dataStore, testCatalog{}, &testRunner{}, reloader)

	queued := model.Job{
		ID: "queued", State: model.JobQueued, Stage: "queued", CreatedAt: time.Now().UTC(),
	}
	if err := dataStore.PutJob(queued); err != nil {
		t.Fatal(err)
	}
	manager.reloadServicesWhenIdle(true)
	if reloader.calls != 0 {
		t.Fatalf("reloader called with an active job")
	}

	queued.State = model.JobFailed
	queued.Stage = "failed"
	if err := dataStore.PutJob(queued); err != nil {
		t.Fatal(err)
	}
	manager.reloadServicesWhenIdle(false)
	if reloader.calls != 1 {
		t.Fatalf("reloader calls = %d, want 1", reloader.calls)
	}

	manager.reloadServicesWhenIdle(false)
	if reloader.calls != 1 {
		t.Fatalf("reloader ran again without another successful operation")
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
