package jobs

import (
	"bytes"
	"context"
	"crypto/rand"
	"encoding/hex"
	"errors"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"sort"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/GameTec-live/atlas/apps/geodataAPI/internal/catalog"
	"github.com/GameTec-live/atlas/apps/geodataAPI/internal/config"
	"github.com/GameTec-live/atlas/apps/geodataAPI/internal/containers"
	"github.com/GameTec-live/atlas/apps/geodataAPI/internal/model"
	"github.com/GameTec-live/atlas/apps/geodataAPI/internal/store"
)

type CommandRunner interface {
	Run(context.Context, string, ...string) error
}

type ServiceReloader interface {
	Restart(context.Context) error
}

type execRunner struct{}

func (execRunner) Run(ctx context.Context, name string, args ...string) error {
	command := exec.CommandContext(ctx, name, args...)
	var output bytes.Buffer
	command.Stdout = &limitedWriter{writer: &output, remaining: 1 << 20}
	command.Stderr = &limitedWriter{writer: &output, remaining: 1 << 20}
	if err := command.Run(); err != nil {
		message := strings.TrimSpace(output.String())
		if message == "" {
			return err
		}
		return fmt.Errorf("%w: %s", err, message)
	}
	return nil
}

type limitedWriter struct {
	writer    io.Writer
	remaining int64
}

func (w *limitedWriter) Write(content []byte) (int, error) {
	original := len(content)
	if w.remaining <= 0 {
		return original, nil
	}
	if int64(len(content)) > w.remaining {
		content = content[:w.remaining]
	}
	_, err := w.writer.Write(content)
	w.remaining -= int64(len(content))
	return original, err
}

type Manager struct {
	cfg      config.Config
	store    *store.Store
	catalog  catalog.Catalog
	runner   CommandRunner
	reloader ServiceReloader
	client   *http.Client

	queue         chan string
	stop          chan struct{}
	done          chan struct{}
	stopOnce      sync.Once
	enqueueMu     sync.Mutex
	reloadPending bool
	mu            sync.Mutex
	cancels       map[string]context.CancelFunc
	subs          map[chan model.Job]struct{}
	subscribers   sync.Mutex
}

func NewManager(cfg config.Config, dataStore *store.Store, regionCatalog catalog.Catalog, runner CommandRunner, reloaders ...ServiceReloader) *Manager {
	if runner == nil {
		runner = execRunner{}
	}
	var reloader ServiceReloader
	if len(reloaders) > 0 {
		reloader = reloaders[0]
	} else if cfg.ContainerSocket != "" {
		reloader = containers.NewClient(cfg.ContainerSocket, cfg.ReloadTimeout)
	}
	return &Manager{
		cfg: cfg, store: dataStore, catalog: regionCatalog, runner: runner,
		reloader: reloader,
		client:   &http.Client{Timeout: cfg.HTTPTimeout},
		queue:    make(chan string, 100), stop: make(chan struct{}), done: make(chan struct{}),
		cancels: make(map[string]context.CancelFunc), subs: make(map[chan model.Job]struct{}),
	}
}

func (m *Manager) Start() { go m.worker() }

func (m *Manager) Stop() {
	m.stopOnce.Do(func() {
		close(m.stop)
		m.mu.Lock()
		for _, cancel := range m.cancels {
			cancel()
		}
		m.mu.Unlock()
		<-m.done
	})
}

func (m *Manager) Install(ctx context.Context, name, id string, bounds *model.Bounds, excludeRoads bool) (model.Job, error) {
	if bounds == nil {
		if strings.TrimSpace(name) == "" {
			return model.Job{}, errors.New("name is required when bbox is omitted")
		}
		region, err := m.catalog.Find(ctx, name)
		if err != nil {
			return model.Job{}, err
		}
		request := model.JobRequest{Name: region.Name, DatasetID: slug(region.ID), Region: &region, ExcludeRoads: excludeRoads}
		return m.enqueue("install", request)
	}

	if strings.TrimSpace(name) != "" {
		return model.Job{}, errors.New("name and bbox are mutually exclusive")
	}
	if !bounds.Valid() {
		return model.Job{}, errors.New("bbox must have minLongitude < maxLongitude and minLatitude < maxLatitude")
	}
	region, err := m.catalog.Covering(ctx, *bounds)
	if err != nil {
		return model.Job{}, err
	}
	if strings.TrimSpace(id) == "" {
		id = fmt.Sprintf("bbox-%s", shortID())
	}
	id = slug(id)
	if id == "" {
		return model.Job{}, errors.New("id must contain at least one letter or number")
	}
	request := model.JobRequest{Name: id, DatasetID: id, Bounds: bounds, Region: &region, ExcludeRoads: excludeRoads}
	return m.enqueue("install", request)
}

func (m *Manager) Delete(datasetID string) (model.Job, error) {
	datasetID = slug(datasetID)
	if _, ok := m.store.Dataset(datasetID); !ok {
		return model.Job{}, fmt.Errorf("dataset %q not found", datasetID)
	}
	return m.enqueue("delete", model.JobRequest{DatasetID: datasetID})
}

func (m *Manager) Cancel(jobID string) (model.Job, error) {
	job, ok := m.store.Job(jobID)
	if !ok {
		return model.Job{}, fmt.Errorf("job %q not found", jobID)
	}
	if job.State == model.JobCompleted || job.State == model.JobFailed || job.State == model.JobCancelled {
		return job, fmt.Errorf("job is already %s", job.State)
	}
	m.mu.Lock()
	cancel := m.cancels[jobID]
	m.mu.Unlock()
	if cancel != nil {
		cancel()
	} else {
		job.State = model.JobCancelled
		job.Stage = "cancelled"
		now := time.Now().UTC()
		job.FinishedAt = &now
		_ = m.saveAndPublish(job)
	}
	return job, nil
}

func (m *Manager) Jobs(activeOnly bool) []model.Job {
	all := m.store.Jobs()
	if !activeOnly {
		return all
	}
	active := make([]model.Job, 0)
	for _, job := range all {
		if job.State == model.JobQueued || job.State == model.JobRunning {
			active = append(active, job)
		}
	}
	return active
}

func (m *Manager) Job(id string) (model.Job, bool) { return m.store.Job(id) }

func (m *Manager) Subscribe() (<-chan model.Job, func()) {
	channel := make(chan model.Job, 16)
	m.subscribers.Lock()
	m.subs[channel] = struct{}{}
	m.subscribers.Unlock()
	return channel, func() {
		m.subscribers.Lock()
		if _, ok := m.subs[channel]; ok {
			delete(m.subs, channel)
			close(channel)
		}
		m.subscribers.Unlock()
	}
}

func (m *Manager) enqueue(operation string, request model.JobRequest) (model.Job, error) {
	m.enqueueMu.Lock()
	defer m.enqueueMu.Unlock()
	if request.DatasetID != "" {
		if _, exists := m.store.Dataset(request.DatasetID); exists && operation == "install" {
			return model.Job{}, fmt.Errorf("dataset %q is already installed", request.DatasetID)
		}
		for _, job := range m.Jobs(true) {
			if job.DatasetID == request.DatasetID {
				return model.Job{}, fmt.Errorf("dataset %q already has an active job", request.DatasetID)
			}
		}
	}
	job := model.Job{
		ID: shortID(), Operation: operation, DatasetID: request.DatasetID,
		State: model.JobQueued, Stage: "queued", CreatedAt: time.Now().UTC(), Request: request,
	}
	if err := m.saveAndPublish(job); err != nil {
		return model.Job{}, err
	}
	select {
	case m.queue <- job.ID:
		return job, nil
	case <-m.stop:
		return model.Job{}, errors.New("job manager is stopping")
	}
}

func (m *Manager) worker() {
	defer close(m.done)
	for {
		select {
		case <-m.stop:
			return
		case id := <-m.queue:
			job, ok := m.store.Job(id)
			if !ok || job.State != model.JobQueued {
				continue
			}
			m.execute(job)
		}
	}
}

func (m *Manager) execute(job model.Job) {
	ctx, cancel := context.WithCancel(context.Background())
	m.mu.Lock()
	m.cancels[job.ID] = cancel
	m.mu.Unlock()
	defer func() {
		cancel()
		m.mu.Lock()
		delete(m.cancels, job.ID)
		m.mu.Unlock()
	}()

	now := time.Now().UTC()
	job.State = model.JobRunning
	job.StartedAt = &now
	job.Stage = "starting"
	_ = m.saveAndPublish(job)

	var err error
	if job.Operation == "delete" {
		err = m.runDelete(ctx, &job)
	} else {
		err = m.runInstall(ctx, &job)
	}
	finished := time.Now().UTC()
	job.FinishedAt = &finished
	if errors.Is(err, context.Canceled) {
		job.State = model.JobCancelled
		job.Stage = "cancelled"
		job.Error = ""
	} else if err != nil {
		job.State = model.JobFailed
		job.Stage = "failed"
		job.Error = err.Error()
	} else {
		job.State = model.JobCompleted
		job.Stage = "completed"
		job.Progress = 1
	}
	_ = m.saveAndPublish(job)
	m.reloadServicesWhenIdle(err == nil)
}

func (m *Manager) reloadServicesWhenIdle(success bool) {
	m.enqueueMu.Lock()
	defer m.enqueueMu.Unlock()
	if success {
		m.reloadPending = true
	}
	if !m.reloadPending || m.reloader == nil || len(m.Jobs(true)) != 0 {
		return
	}
	m.reloadPending = false
	ctx, cancel := context.WithTimeout(context.Background(), m.cfg.ReloadTimeout)
	defer cancel()
	if err := m.reloader.Restart(ctx); err != nil {
		slog.Warn("could not reload geodata consumers", "error", err)
	}
}

func (m *Manager) runInstall(ctx context.Context, job *model.Job) (runErr error) {
	request := job.Request
	if request.Region == nil {
		return errors.New("job has no source region")
	}
	workDir := filepath.Join(m.store.TempDir(), job.ID)
	if err := os.MkdirAll(workDir, 0o755); err != nil {
		return err
	}
	defer os.RemoveAll(workDir)

	finalPBF := filepath.Join(m.store.Root(), request.DatasetID+".osm.pbf")
	finalGeocoder := filepath.Join(m.store.Root(), request.DatasetID+".sqlite")
	defer func() {
		if runErr != nil {
			_ = os.Remove(finalPBF)
			_ = os.Remove(finalGeocoder)
		}
	}()
	downloadTarget := finalPBF + ".part"
	if request.Bounds != nil {
		downloadTarget = filepath.Join(workDir, "source.osm.pbf")
	}
	job.Stage = "downloading_pbf"
	job.Progress = 0.02
	_ = m.saveAndPublish(*job)
	if err := m.download(ctx, request.Region.PBFURL, downloadTarget, job); err != nil {
		_ = os.Remove(downloadTarget)
		return err
	}

	if request.Bounds != nil {
		job.Stage = "extracting_bbox"
		job.Progress = 0.52
		_ = m.saveAndPublish(*job)
		bbox := fmt.Sprintf("%g,%g,%g,%g", request.Bounds.MinLongitude, request.Bounds.MinLatitude, request.Bounds.MaxLongitude, request.Bounds.MaxLatitude)
		if err := m.runner.Run(ctx, m.cfg.OsmiumBinary, "extract", "--bbox", bbox, "--set-bounds", "--overwrite", "--output", finalPBF, downloadTarget); err != nil {
			return fmt.Errorf("extract bounding box with osmium: %w", err)
		}
	} else if err := replace(downloadTarget, finalPBF); err != nil {
		return fmt.Errorf("commit PBF: %w", err)
	}

	artifacts := make([]model.Artifact, 0, 3)
	pbfArtifact, err := m.store.Artifact(model.ArtifactPBF, request.DatasetID+".osm.pbf")
	if err != nil {
		return err
	}
	artifacts = append(artifacts, pbfArtifact)

	job.Stage = "building_geocoder"
	job.Progress = 0.65
	_ = m.saveAndPublish(*job)
	relative := request.DatasetID + ".sqlite"
	temporary := filepath.Join(workDir, request.DatasetID+".sqlite.building")
	args := []string{"build", "--source", "openstreetmap", "--output", temporary}
	if len(request.Region.CountryCodes) > 0 {
		args = append(args, "--country", request.Region.CountryCodes[0])
	}
	if request.ExcludeRoads {
		args = append(args, "--include-roads=false")
	}
	args = append(args, finalPBF)
	if err := m.runner.Run(ctx, m.cfg.PackgenBinary, args...); err != nil {
		return fmt.Errorf("build geocoder pack: %w", err)
	}
	if err := replace(temporary, finalGeocoder); err != nil {
		return fmt.Errorf("commit geocoder pack: %w", err)
	}
	geocoderArtifact, err := m.store.Artifact(model.ArtifactGeocoder, relative)
	if err != nil {
		return err
	}
	artifacts = append(artifacts, geocoderArtifact)

	job.Stage = "building_map"
	job.Progress = 0.82
	_ = m.saveAndPublish(*job)
	if err := m.buildMap(ctx, workDir, finalPBF); err != nil {
		return err
	}
	mapArtifact, err := m.store.Artifact(model.ArtifactMap, "map.pmtiles")
	if err != nil {
		return err
	}
	artifacts = append(artifacts, mapArtifact)

	countryCode := ""
	if len(request.Region.CountryCodes) > 0 {
		countryCode = request.Region.CountryCodes[0]
	}
	dataset := model.Dataset{
		ID: request.DatasetID, Name: request.Name, SourceURL: request.Region.PBFURL,
		SourceRegion: request.Region.ID, CountryCode: countryCode, Bounds: request.Bounds,
		ExcludeRoads: request.ExcludeRoads, Artifacts: artifacts, InstalledAt: time.Now().UTC(),
	}
	if request.Bounds == nil {
		dataset.SourceType = "name"
	} else {
		dataset.SourceType = "bbox"
	}
	if err := m.store.PutDataset(dataset); err != nil {
		return err
	}
	return nil
}

func (m *Manager) runDelete(ctx context.Context, job *model.Job) error {
	dataset, ok := m.store.Dataset(job.DatasetID)
	if !ok {
		return fmt.Errorf("dataset %q not found", job.DatasetID)
	}

	remaining := make([]model.Dataset, 0)
	for _, candidate := range m.store.Datasets() {
		if candidate.ID != dataset.ID {
			remaining = append(remaining, candidate)
		}
	}
	pbfs := datasetPBFs(m.store.Root(), remaining)
	var replacementMap string
	if len(pbfs) > 0 {
		job.Stage = "rebuilding_map"
		job.Progress = 0.25
		_ = m.saveAndPublish(*job)
		workDir := filepath.Join(m.store.TempDir(), job.ID)
		if err := os.MkdirAll(workDir, 0o755); err != nil {
			return err
		}
		defer os.RemoveAll(workDir)
		var err error
		replacementMap, err = m.generateMap(ctx, workDir, pbfs)
		if err != nil {
			return err
		}
	}

	job.Stage = "deleting_files"
	job.Progress = 0.85
	_ = m.saveAndPublish(*job)
	for _, artifact := range dataset.Artifacts {
		if artifact.Kind == model.ArtifactMap {
			continue
		}
		if err := os.Remove(filepath.Join(m.store.Root(), filepath.FromSlash(artifact.Path))); err != nil && !errors.Is(err, os.ErrNotExist) {
			return err
		}
	}
	if err := m.store.RemoveDataset(dataset.ID); err != nil {
		return err
	}

	mapPath := filepath.Join(m.store.Root(), "map.pmtiles")
	if len(pbfs) == 0 {
		if err := os.Remove(mapPath); err != nil && !errors.Is(err, os.ErrNotExist) {
			return err
		}
		return nil
	}
	if err := replace(replacementMap, mapPath); err != nil {
		return fmt.Errorf("commit map.pmtiles: %w", err)
	}
	return nil
}

func (m *Manager) download(ctx context.Context, sourceURL, destination string, job *model.Job) error {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, sourceURL, nil)
	if err != nil {
		return err
	}
	req.Header.Set("User-Agent", "atlas-geodata-api/1.0")
	resp, err := m.client.Do(req)
	if err != nil {
		return fmt.Errorf("download PBF: %w", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		_, _ = io.Copy(io.Discard, resp.Body)
		return fmt.Errorf("download PBF: HTTP %d", resp.StatusCode)
	}
	file, err := os.Create(destination)
	if err != nil {
		return err
	}
	defer file.Close()

	job.BytesTotal = resp.ContentLength
	buffer := make([]byte, 256*1024)
	lastUpdate := time.Now()
	for {
		count, readErr := resp.Body.Read(buffer)
		if count > 0 {
			if _, err := file.Write(buffer[:count]); err != nil {
				return err
			}
			job.BytesDone += int64(count)
			if time.Since(lastUpdate) >= 500*time.Millisecond {
				if job.BytesTotal > 0 {
					job.Progress = 0.02 + 0.48*(float64(job.BytesDone)/float64(job.BytesTotal))
				}
				_ = m.saveAndPublish(*job)
				lastUpdate = time.Now()
			}
		}
		if errors.Is(readErr, io.EOF) {
			break
		}
		if readErr != nil {
			return readErr
		}
	}
	return file.Sync()
}

func (m *Manager) buildMap(ctx context.Context, workDir, candidatePBF string) error {
	pbfs := datasetPBFs(m.store.Root(), m.store.Datasets())
	pbfs = append(pbfs, candidatePBF)
	unique := make(map[string]struct{}, len(pbfs))
	filtered := pbfs[:0]
	for _, path := range pbfs {
		path, _ = filepath.Abs(path)
		if _, exists := unique[path]; !exists {
			unique[path] = struct{}{}
			filtered = append(filtered, path)
		}
	}
	return m.buildMapFromPaths(ctx, workDir, filtered)
}

func (m *Manager) buildMapFromPaths(ctx context.Context, workDir string, pbfs []string) error {
	temporary, err := m.generateMap(ctx, workDir, pbfs)
	if err != nil {
		return err
	}
	if err := replace(temporary, filepath.Join(m.store.Root(), "map.pmtiles")); err != nil {
		return fmt.Errorf("commit map.pmtiles: %w", err)
	}
	return nil
}

func (m *Manager) generateMap(ctx context.Context, workDir string, pbfs []string) (string, error) {
	if strings.TrimSpace(m.cfg.PlanetilerJar) == "" {
		return "", errors.New("map generation requested but GEODATA_PLANETILER_JAR is not configured")
	}
	sort.Strings(pbfs)
	input := pbfs[0]
	if len(pbfs) > 1 {
		input = filepath.Join(workDir, "combined.osm.pbf")
		args := append([]string{"merge", "--overwrite", "--output", input}, pbfs...)
		if err := m.runner.Run(ctx, m.cfg.OsmiumBinary, args...); err != nil {
			return "", fmt.Errorf("merge PBF files with osmium: %w", err)
		}
	}
	temporary := filepath.Join(workDir, "map.pmtiles")
	args := []string{"-jar", m.cfg.PlanetilerJar, "--download", "--osm-path=" + input, "--output=" + temporary, "--force"}
	args = append(args, m.cfg.PlanetilerArgs...)
	if err := m.runner.Run(ctx, m.cfg.JavaBinary, args...); err != nil {
		return "", fmt.Errorf("build PMTiles with Planetiler: %w", err)
	}
	return temporary, nil
}

func datasetPBFs(root string, datasets []model.Dataset) []string {
	var paths []string
	for _, dataset := range datasets {
		for _, artifact := range dataset.Artifacts {
			if artifact.Kind == model.ArtifactPBF {
				path := filepath.Join(root, filepath.FromSlash(artifact.Path))
				if _, err := os.Stat(path); err == nil {
					paths = append(paths, path)
				}
			}
		}
	}
	return paths
}

func (m *Manager) saveAndPublish(job model.Job) error {
	if err := m.store.PutJob(job); err != nil {
		return err
	}
	m.subscribers.Lock()
	for subscriber := range m.subs {
		select {
		case subscriber <- job:
		default:
		}
	}
	m.subscribers.Unlock()
	return nil
}

func shortID() string {
	content := make([]byte, 8)
	if _, err := rand.Read(content); err == nil {
		return hex.EncodeToString(content)
	}
	return strconv.FormatInt(time.Now().UnixNano(), 36)
}

func slug(value string) string {
	value = strings.ToLower(strings.TrimSpace(value))
	var result strings.Builder
	lastDash := false
	for _, char := range value {
		if (char >= 'a' && char <= 'z') || (char >= '0' && char <= '9') {
			result.WriteRune(char)
			lastDash = false
		} else if !lastDash && result.Len() > 0 {
			result.WriteByte('-')
			lastDash = true
		}
	}
	return strings.Trim(result.String(), "-")
}

func replace(source, destination string) error {
	if err := os.Rename(source, destination); err == nil {
		return nil
	}
	if err := os.Remove(destination); err != nil && !errors.Is(err, os.ErrNotExist) {
		return err
	}
	return os.Rename(source, destination)
}
