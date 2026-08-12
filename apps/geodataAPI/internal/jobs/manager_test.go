package jobs

import (
	"context"
	"errors"
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
	afterRun func(string)
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

func (r *testReloader) count() int {
	r.mu.Lock()
	defer r.mu.Unlock()
	return r.calls
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
	if err := os.WriteFile(output, []byte(name+" output"), 0o644); err != nil {
		return err
	}
	r.mu.Lock()
	afterRun := r.afterRun
	r.mu.Unlock()
	if afterRun != nil {
		afterRun(name)
	}
	return nil
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
	if !strings.Contains(calls, "osmium time-filter") || !strings.Contains(calls, "--output-format=pbf,history=true") {
		t.Fatalf("second install did not collapse overlapping PBF versions:\n%s", calls)
	}
	if !strings.Contains(calls, "packgen build --source openstreetmap --format pbf") || !strings.Contains(calls, "--include-roads=false") {
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

func TestUpdateChecksValidatorsAndReplacesArtifactsSafely(t *testing.T) {
	var sourceMu sync.Mutex
	sourceVersion := 1
	pbf := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, request *http.Request) {
		sourceMu.Lock()
		version := sourceVersion
		sourceMu.Unlock()
		etag := fmt.Sprintf(`"version-%d"`, version)
		w.Header().Set("ETag", etag)
		w.Header().Set("Last-Modified", fmt.Sprintf("Tue, %02d Aug 2026 10:00:00 GMT", version))
		if request.Header.Get("If-None-Match") == etag {
			w.WriteHeader(http.StatusNotModified)
			return
		}
		_, _ = fmt.Fprintf(w, "pbf-version-%d", version)
	}))
	defer pbf.Close()

	root := t.TempDir()
	dataStore, err := store.Open(root)
	if err != nil {
		t.Fatal(err)
	}
	regionCatalog := testCatalog{regions: map[string]model.Region{
		"one": {ID: "one", Name: "One", PBFURL: pbf.URL, CountryCodes: []string{"AT"}},
	}}
	runner := &testRunner{}
	reloader := &testReloader{}
	cfg := config.Config{DataDir: root, HTTPTimeout: time.Second, OsmiumBinary: "osmium", PackgenBinary: "packgen", JavaBinary: "java", PlanetilerJar: "planetiler.jar"}
	manager := NewManager(cfg, dataStore, regionCatalog, runner, reloader)
	manager.Start()
	defer manager.Stop()

	install, err := manager.Install(context.Background(), "one", "", nil, true)
	if err != nil {
		t.Fatal(err)
	}
	if job := waitJob(t, manager, install.ID); job.State != model.JobCompleted {
		t.Fatalf("install: %#v", job)
	}
	waitReloadCalls(t, reloader, 1)
	installed, _ := dataStore.Dataset("one")
	if installed.SourceETag != `"version-1"` {
		t.Fatalf("initial source ETag = %q", installed.SourceETag)
	}

	check, err := manager.Update("one")
	if err != nil {
		t.Fatal(err)
	}
	if job := waitJob(t, manager, check.ID); job.State != model.JobCompleted || job.Stage != "up_to_date" {
		t.Fatalf("unchanged update: %#v", job)
	}
	if calls := reloader.count(); calls != 1 {
		t.Fatalf("unchanged update reloaded consumers: %d calls", calls)
	}

	sourceMu.Lock()
	sourceVersion = 2
	sourceMu.Unlock()
	update, err := manager.Update("one")
	if err != nil {
		t.Fatal(err)
	}
	if job := waitJob(t, manager, update.ID); job.State != model.JobCompleted || job.Stage != "completed" {
		t.Fatalf("changed update: %#v", job)
	}
	waitReloadCalls(t, reloader, 2)
	content, err := os.ReadFile(filepath.Join(root, "one.osm.pbf"))
	if err != nil || string(content) != "pbf-version-2" {
		t.Fatalf("updated PBF = %q, %v", content, err)
	}
	updated, _ := dataStore.Dataset("one")
	if updated.SourceETag != `"version-2"` || updated.UpdatedAt == nil || updated.LastCheckedAt == nil {
		t.Fatalf("updated source metadata: %#v", updated)
	}
	runner.mu.Lock()
	calls := strings.Join(runner.calls, "\n")
	runner.mu.Unlock()
	if strings.Count(calls, "--include-roads=false") != 2 {
		t.Fatalf("update did not preserve excludeRoads:\n%s", calls)
	}

	before := make(map[string][]byte)
	for _, name := range []string{"one.osm.pbf", "one.sqlite", "map.pmtiles"} {
		before[name], err = os.ReadFile(filepath.Join(root, name))
		if err != nil {
			t.Fatal(err)
		}
	}
	sourceMu.Lock()
	sourceVersion = 3
	sourceMu.Unlock()
	runner.setFailJava(true)
	failed, err := manager.Update("one")
	if err != nil {
		t.Fatal(err)
	}
	if job := waitJob(t, manager, failed.ID); job.State != model.JobFailed {
		t.Fatalf("failed update: %#v", job)
	}
	for name, expected := range before {
		content, readErr := os.ReadFile(filepath.Join(root, name))
		if readErr != nil || string(content) != string(expected) {
			t.Fatalf("failed update changed %s: %q, %v", name, content, readErr)
		}
	}
	afterFailure, _ := dataStore.Dataset("one")
	if afterFailure.SourceETag != `"version-2"` {
		t.Fatalf("failed update changed source metadata: %#v", afterFailure)
	}
	if calls := reloader.count(); calls != 2 {
		t.Fatalf("failed update reloaded consumers: %d calls", calls)
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

func TestFileReplacementsRollbackAfterPartialCommit(t *testing.T) {
	root := t.TempDir()
	workDir := filepath.Join(root, "work")
	if err := os.Mkdir(workDir, 0o755); err != nil {
		t.Fatal(err)
	}
	first := filepath.Join(root, "first")
	second := filepath.Join(root, "second")
	candidate := filepath.Join(workDir, "candidate-first")
	for path, content := range map[string]string{
		first: "old-first", second: "old-second", candidate: "new-first",
	} {
		if err := os.WriteFile(path, []byte(content), 0o644); err != nil {
			t.Fatal(err)
		}
	}
	_, err := applyFileReplacements(workDir, []fileReplacement{
		{candidate: candidate, destination: first},
		{candidate: filepath.Join(workDir, "missing"), destination: second},
	})
	if err == nil {
		t.Fatal("partial replacement unexpectedly succeeded")
	}
	for path, expected := range map[string]string{first: "old-first", second: "old-second"} {
		content, readErr := os.ReadFile(path)
		if readErr != nil || string(content) != expected {
			t.Fatalf("rollback left %s as %q, %v", path, content, readErr)
		}
	}
}

func TestInstallRestoresSharedMapWhenDatasetPersistenceFails(t *testing.T) {
	pbf := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		_, _ = w.Write([]byte("new-pbf"))
	}))
	defer pbf.Close()

	root := t.TempDir()
	dataStore, err := store.Open(root)
	if err != nil {
		t.Fatal(err)
	}
	mapPath := filepath.Join(root, "map.pmtiles")
	if err := os.WriteFile(mapPath, []byte("old-map"), 0o644); err != nil {
		t.Fatal(err)
	}
	stateBlocker := filepath.Join(root, ".geodata", "state.json.tmp")
	var stagedPBFFiles []string
	runner := &testRunner{}
	runner.afterRun = func(name string) {
		if name == "java" {
			if err := filepath.WalkDir(filepath.Join(root, ".geodata"), func(path string, entry os.DirEntry, walkErr error) error {
				if walkErr != nil {
					return walkErr
				}
				if !entry.IsDir() && strings.EqualFold(filepath.Ext(entry.Name()), ".pbf") {
					stagedPBFFiles = append(stagedPBFFiles, path)
				}
				return nil
			}); err != nil {
				t.Errorf("inspect staged files: %v", err)
			}
			if err := os.Mkdir(stateBlocker, 0o755); err != nil {
				t.Errorf("block state persistence: %v", err)
			}
		}
	}
	manager := NewManager(
		config.Config{DataDir: root, HTTPTimeout: time.Second, PackgenBinary: "packgen", JavaBinary: "java", PlanetilerJar: "planetiler.jar"},
		dataStore,
		testCatalog{},
		runner,
	)
	region := model.Region{ID: "new", Name: "New", PBFURL: pbf.URL}
	job := model.Job{ID: "install", DatasetID: "new", Request: model.JobRequest{
		Name: "New", DatasetID: "new", SourceType: "catalog", Region: &region,
	}}

	if err := manager.runInstall(context.Background(), &job); err == nil {
		t.Fatal("install unexpectedly succeeded")
	}
	if len(stagedPBFFiles) != 0 {
		t.Fatalf("staged PBFs must not use the .pbf extension: %v", stagedPBFFiles)
	}
	if _, found := dataStore.Dataset("new"); found {
		t.Fatal("failed install remained registered in memory")
	}
	for _, name := range []string{"new.osm.pbf", "new.sqlite"} {
		if _, err := os.Stat(filepath.Join(root, name)); !errors.Is(err, os.ErrNotExist) {
			t.Fatalf("failed install published %s: %v", name, err)
		}
	}
	if content, err := os.ReadFile(mapPath); err != nil || string(content) != "old-map" {
		t.Fatalf("failed install did not restore map: %q, %v", content, err)
	}
	if err := os.Remove(stateBlocker); err != nil {
		t.Fatal(err)
	}
	reopened, err := store.Open(root)
	if err != nil {
		t.Fatal(err)
	}
	if _, found := reopened.Dataset("new"); found {
		t.Fatal("failed install was durably registered")
	}
}

func TestDeleteRestoresStagedArtifactsAfterLaterRemovalFails(t *testing.T) {
	root := t.TempDir()
	dataStore, err := store.Open(root)
	if err != nil {
		t.Fatal(err)
	}
	for name, content := range map[string]string{
		"one.osm.pbf": "pbf", "map.pmtiles": "map",
	} {
		if err := os.WriteFile(filepath.Join(root, name), []byte(content), 0o644); err != nil {
			t.Fatal(err)
		}
	}
	dataset := model.Dataset{ID: "one", Artifacts: []model.Artifact{
		{Kind: model.ArtifactPBF, Path: "one.osm.pbf"},
		// Renaming the data root into its own transaction directory must fail,
		// after the preceding PBF removal has already been staged.
		{Kind: model.ArtifactGeocoder, Path: "."},
		{Kind: model.ArtifactMap, Path: "map.pmtiles"},
	}}
	if err := dataStore.PutDataset(dataset); err != nil {
		t.Fatal(err)
	}
	manager := NewManager(config.Config{DataDir: root}, dataStore, testCatalog{}, &testRunner{})
	job := model.Job{ID: "delete", DatasetID: "one"}

	if err := manager.runDelete(context.Background(), &job); err == nil {
		t.Fatal("delete unexpectedly succeeded")
	}
	if _, found := dataStore.Dataset("one"); !found {
		t.Fatal("failed delete removed dataset metadata")
	}
	for name, expected := range map[string]string{"one.osm.pbf": "pbf", "map.pmtiles": "map"} {
		content, readErr := os.ReadFile(filepath.Join(root, name))
		if readErr != nil || string(content) != expected {
			t.Fatalf("failed delete changed %s: %q, %v", name, content, readErr)
		}
	}
}

func TestDeleteRestoresFilesWhenDatasetPersistenceFails(t *testing.T) {
	root := t.TempDir()
	dataStore, err := store.Open(root)
	if err != nil {
		t.Fatal(err)
	}
	for name, content := range map[string]string{
		"one.osm.pbf": "pbf", "one.sqlite": "geocoder", "map.pmtiles": "map",
	} {
		if err := os.WriteFile(filepath.Join(root, name), []byte(content), 0o644); err != nil {
			t.Fatal(err)
		}
	}
	dataset := model.Dataset{ID: "one", Artifacts: []model.Artifact{
		{Kind: model.ArtifactPBF, Path: "one.osm.pbf"},
		{Kind: model.ArtifactGeocoder, Path: "one.sqlite"},
		{Kind: model.ArtifactMap, Path: "map.pmtiles"},
	}}
	if err := dataStore.PutDataset(dataset); err != nil {
		t.Fatal(err)
	}
	stateBlocker := filepath.Join(root, ".geodata", "state.json.tmp")
	if err := os.Mkdir(stateBlocker, 0o755); err != nil {
		t.Fatal(err)
	}
	manager := NewManager(config.Config{DataDir: root}, dataStore, testCatalog{}, &testRunner{})
	job := model.Job{ID: "delete", DatasetID: "one"}

	if err := manager.runDelete(context.Background(), &job); err == nil {
		t.Fatal("delete unexpectedly succeeded")
	}
	if _, found := dataStore.Dataset("one"); !found {
		t.Fatal("failed delete removed dataset metadata from memory")
	}
	for name, expected := range map[string]string{
		"one.osm.pbf": "pbf", "one.sqlite": "geocoder", "map.pmtiles": "map",
	} {
		content, readErr := os.ReadFile(filepath.Join(root, name))
		if readErr != nil || string(content) != expected {
			t.Fatalf("failed delete changed %s: %q, %v", name, content, readErr)
		}
	}
	if err := os.Remove(stateBlocker); err != nil {
		t.Fatal(err)
	}
	reopened, err := store.Open(root)
	if err != nil {
		t.Fatal(err)
	}
	if _, found := reopened.Dataset("one"); !found {
		t.Fatal("failed delete durably removed dataset metadata")
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

func waitReloadCalls(t *testing.T, reloader *testReloader, expected int) {
	t.Helper()
	deadline := time.Now().Add(time.Second)
	for time.Now().Before(deadline) {
		if reloader.count() == expected {
			return
		}
		time.Sleep(time.Millisecond)
	}
	t.Fatalf("reloader calls = %d, want %d", reloader.count(), expected)
}
