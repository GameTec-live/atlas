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
	"net/url"
	"os"
	"os/exec"
	"path"
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

type sourceVersion struct {
	ETag         string
	LastModified string
}

var ErrDatasetNotFound = errors.New("dataset not found")
var ErrInvalidInstallSource = errors.New("invalid install source")

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

func (m *Manager) Install(ctx context.Context, id, sourceURL string, bounds *model.Bounds, excludeRoads bool) (model.Job, error) {
	if bounds == nil {
		if strings.TrimSpace(sourceURL) != "" {
			region, err := customRegion(sourceURL)
			if err != nil {
				return model.Job{}, err
			}
			request := model.JobRequest{
				Name: region.Name, DatasetID: region.ID, SourceType: "url", Region: &region, ExcludeRoads: excludeRoads,
			}
			return m.enqueue("install", request)
		}
		if strings.TrimSpace(id) == "" {
			return model.Job{}, fmt.Errorf("%w: id is required when url and bbox are omitted", ErrInvalidInstallSource)
		}
		region, err := m.catalog.Find(ctx, id)
		if err != nil {
			return model.Job{}, err
		}
		request := model.JobRequest{Name: region.Name, DatasetID: slug(region.ID), SourceType: "catalog", Region: &region, ExcludeRoads: excludeRoads}
		return m.enqueue("install", request)
	}

	if strings.TrimSpace(sourceURL) != "" {
		return model.Job{}, fmt.Errorf("%w: url and bbox are mutually exclusive", ErrInvalidInstallSource)
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
	request := model.JobRequest{Name: id, DatasetID: id, SourceType: "bbox", Bounds: bounds, Region: &region, ExcludeRoads: excludeRoads}
	return m.enqueue("install", request)
}

func customRegion(rawURL string) (model.Region, error) {
	rawURL = strings.TrimSpace(rawURL)
	parsed, err := url.Parse(rawURL)
	if err != nil || (!strings.EqualFold(parsed.Scheme, "http") && !strings.EqualFold(parsed.Scheme, "https")) || parsed.Host == "" || parsed.Fragment != "" {
		return model.Region{}, fmt.Errorf("%w: url must be an absolute HTTP or HTTPS URL without a fragment", ErrInvalidInstallSource)
	}
	filename := path.Base(parsed.Path)
	lowerName := strings.ToLower(filename)
	if !strings.HasSuffix(lowerName, ".pbf") {
		return model.Region{}, fmt.Errorf("%w: url path must end in .pbf", ErrInvalidInstallSource)
	}
	name := filename[:len(filename)-len(".pbf")]
	if strings.HasSuffix(strings.ToLower(name), ".osm") {
		name = name[:len(name)-len(".osm")]
	}
	if strings.HasSuffix(strings.ToLower(name), "-latest") {
		name = name[:len(name)-len("-latest")]
	}
	id := slug(name)
	if id == "" {
		return model.Region{}, fmt.Errorf("%w: PBF filename must contain at least one letter or number", ErrInvalidInstallSource)
	}
	return model.Region{ID: id, Name: id, PBFURL: parsed.String()}, nil
}

func (m *Manager) Delete(datasetID string) (model.Job, error) {
	datasetID = slug(datasetID)
	if _, ok := m.store.Dataset(datasetID); !ok {
		return model.Job{}, fmt.Errorf("%w: %q", ErrDatasetNotFound, datasetID)
	}
	return m.enqueue("delete", model.JobRequest{DatasetID: datasetID})
}

func (m *Manager) Update(datasetID string) (model.Job, error) {
	datasetID = slug(datasetID)
	if _, ok := m.store.Dataset(datasetID); !ok {
		return model.Job{}, fmt.Errorf("%w: %q", ErrDatasetNotFound, datasetID)
	}
	return m.enqueue("update", model.JobRequest{DatasetID: datasetID})
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
	changed := false
	if job.Operation == "delete" {
		err = m.runDelete(ctx, &job)
		changed = err == nil
	} else if job.Operation == "update" {
		changed, err = m.runUpdate(ctx, &job)
	} else {
		err = m.runInstall(ctx, &job)
		changed = err == nil
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
		if job.Operation == "update" && !changed {
			job.Stage = "up_to_date"
		} else {
			job.Stage = "completed"
		}
		job.Progress = 1
	}
	_ = m.saveAndPublish(job)
	m.reloadServicesWhenIdle(err == nil && changed)
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

func (m *Manager) runInstall(ctx context.Context, job *model.Job) error {
	request := job.Request
	if request.Region == nil {
		return errors.New("job has no source region")
	}
	workDir := filepath.Join(m.store.TempDir(), job.ID)
	if err := os.MkdirAll(workDir, 0o755); err != nil {
		return err
	}
	defer os.RemoveAll(workDir)

	candidatePBF := stagedPBFPath(workDir, "dataset")
	downloadTarget := candidatePBF
	if request.Bounds != nil {
		downloadTarget = stagedPBFPath(workDir, "source")
	}
	job.Stage = "downloading_pbf"
	job.Progress = 0.02
	_ = m.saveAndPublish(*job)
	version, _, err := m.download(ctx, request.Region.PBFURL, downloadTarget, job, sourceVersion{})
	if err != nil {
		_ = os.Remove(downloadTarget)
		return err
	}

	if request.Bounds != nil {
		job.Stage = "extracting_bbox"
		job.Progress = 0.52
		_ = m.saveAndPublish(*job)
		bbox := fmt.Sprintf("%g,%g,%g,%g", request.Bounds.MinLongitude, request.Bounds.MinLatitude, request.Bounds.MaxLongitude, request.Bounds.MaxLatitude)
		if err := m.runner.Run(ctx, m.cfg.OsmiumBinary, "extract", "--bbox", bbox, "--set-bounds", "--overwrite", "--input-format=pbf", "--output-format=pbf", "--output", candidatePBF, downloadTarget); err != nil {
			return fmt.Errorf("extract bounding box with osmium: %w", err)
		}
	}

	artifacts := make([]model.Artifact, 0, 3)
	pbfArtifact, err := artifactFromCandidate(model.ArtifactPBF, request.DatasetID+".osm.pbf", candidatePBF)
	if err != nil {
		return err
	}
	artifacts = append(artifacts, pbfArtifact)

	job.Stage = "building_geocoder"
	job.Progress = 0.65
	_ = m.saveAndPublish(*job)
	relative := request.DatasetID + ".sqlite"
	candidateGeocoder := filepath.Join(workDir, request.DatasetID+".sqlite")
	countryCode := ""
	if len(request.Region.CountryCodes) > 0 {
		countryCode = request.Region.CountryCodes[0]
	}
	if err := m.buildGeocoder(ctx, candidatePBF, candidateGeocoder, countryCode, request.ExcludeRoads); err != nil {
		return err
	}
	geocoderArtifact, err := artifactFromCandidate(model.ArtifactGeocoder, relative, candidateGeocoder)
	if err != nil {
		return err
	}
	artifacts = append(artifacts, geocoderArtifact)

	job.Stage = "building_map"
	job.Progress = 0.82
	_ = m.saveAndPublish(*job)
	pbfs := datasetPBFs(m.store.Root(), m.store.Datasets())
	pbfs = append(pbfs, candidatePBF)
	candidateMap, err := m.generateMap(ctx, workDir, pbfs)
	if err != nil {
		return err
	}
	mapArtifact, err := artifactFromCandidate(model.ArtifactMap, "map.pmtiles", candidateMap)
	if err != nil {
		return err
	}
	artifacts = append(artifacts, mapArtifact)

	now := time.Now().UTC()
	dataset := model.Dataset{
		ID: request.DatasetID, Name: request.Name, SourceURL: request.Region.PBFURL,
		SourceRegion: request.Region.ID, CountryCode: countryCode, Bounds: request.Bounds,
		ExcludeRoads: request.ExcludeRoads, SourceETag: version.ETag, SourceLastModified: version.LastModified,
		LastCheckedAt: &now, UpdatedAt: &now, Artifacts: artifacts, InstalledAt: now,
	}
	dataset.SourceType = request.SourceType
	if dataset.SourceType == "" {
		if request.Bounds == nil {
			dataset.SourceType = "catalog"
		} else {
			dataset.SourceType = "bbox"
		}
	}
	if dataset.SourceType == "url" {
		dataset.SourceRegion = ""
	}

	job.Stage = "committing"
	job.Progress = 0.95
	_ = m.saveAndPublish(*job)
	transaction, err := applyFileReplacements(workDir, []fileReplacement{
		{candidate: candidatePBF, destination: filepath.Join(m.store.Root(), request.DatasetID+".osm.pbf")},
		{candidate: candidateGeocoder, destination: filepath.Join(m.store.Root(), request.DatasetID+".sqlite")},
		{candidate: candidateMap, destination: filepath.Join(m.store.Root(), "map.pmtiles")},
	})
	if err != nil {
		return fmt.Errorf("commit installed artifacts: %w", err)
	}
	if err := m.store.PutDataset(dataset); err != nil {
		return errors.Join(err, transaction.Rollback())
	}
	transaction.Commit()
	return nil
}

func (m *Manager) runUpdate(ctx context.Context, job *model.Job) (bool, error) {
	dataset, ok := m.store.Dataset(job.DatasetID)
	if !ok {
		return false, fmt.Errorf("dataset %q not found", job.DatasetID)
	}
	workDir := filepath.Join(m.store.TempDir(), job.ID)
	if err := os.MkdirAll(workDir, 0o755); err != nil {
		return false, err
	}
	defer os.RemoveAll(workDir)

	job.Stage = "checking_update"
	job.Progress = 0.02
	_ = m.saveAndPublish(*job)
	downloadTarget := stagedPBFPath(workDir, "updated")
	if dataset.Bounds != nil {
		downloadTarget = stagedPBFPath(workDir, "source")
	}
	version, changed, err := m.download(ctx, dataset.SourceURL, downloadTarget, job, sourceVersion{
		ETag: dataset.SourceETag, LastModified: dataset.SourceLastModified,
	})
	if err != nil {
		return false, err
	}
	now := time.Now().UTC()
	if !changed {
		dataset.LastCheckedAt = &now
		if err := m.store.PutDataset(dataset); err != nil {
			return false, err
		}
		job.Stage = "up_to_date"
		job.Progress = 1
		_ = m.saveAndPublish(*job)
		return false, nil
	}

	candidatePBF := downloadTarget
	if dataset.Bounds != nil {
		job.Stage = "extracting_bbox"
		job.Progress = 0.52
		_ = m.saveAndPublish(*job)
		candidatePBF = stagedPBFPath(workDir, "dataset")
		bbox := fmt.Sprintf("%g,%g,%g,%g", dataset.Bounds.MinLongitude, dataset.Bounds.MinLatitude, dataset.Bounds.MaxLongitude, dataset.Bounds.MaxLatitude)
		if err := m.runner.Run(ctx, m.cfg.OsmiumBinary, "extract", "--bbox", bbox, "--set-bounds", "--overwrite", "--input-format=pbf", "--output-format=pbf", "--output", candidatePBF, downloadTarget); err != nil {
			return false, fmt.Errorf("extract bounding box with osmium: %w", err)
		}
	}

	job.Stage = "building_geocoder"
	job.Progress = 0.65
	_ = m.saveAndPublish(*job)
	candidateGeocoder := filepath.Join(workDir, dataset.ID+".sqlite")
	if err := m.buildGeocoder(ctx, candidatePBF, candidateGeocoder, dataset.CountryCode, dataset.ExcludeRoads); err != nil {
		return false, err
	}

	job.Stage = "building_map"
	job.Progress = 0.82
	_ = m.saveAndPublish(*job)
	pbfs := datasetPBFsExcept(m.store.Root(), m.store.Datasets(), dataset.ID)
	pbfs = append(pbfs, candidatePBF)
	candidateMap, err := m.generateMap(ctx, workDir, pbfs)
	if err != nil {
		return false, err
	}

	job.Stage = "committing"
	job.Progress = 0.95
	_ = m.saveAndPublish(*job)
	transaction, err := applyFileReplacements(workDir, []fileReplacement{
		{candidate: candidatePBF, destination: filepath.Join(m.store.Root(), dataset.ID+".osm.pbf")},
		{candidate: candidateGeocoder, destination: filepath.Join(m.store.Root(), dataset.ID+".sqlite")},
		{candidate: candidateMap, destination: filepath.Join(m.store.Root(), "map.pmtiles")},
	})
	if err != nil {
		return false, fmt.Errorf("commit updated artifacts: %w", err)
	}

	artifacts := make([]model.Artifact, 0, 3)
	for _, item := range []struct {
		kind model.ArtifactKind
		path string
	}{
		{model.ArtifactPBF, dataset.ID + ".osm.pbf"},
		{model.ArtifactGeocoder, dataset.ID + ".sqlite"},
		{model.ArtifactMap, "map.pmtiles"},
	} {
		artifact, artifactErr := m.store.Artifact(item.kind, item.path)
		if artifactErr != nil {
			return false, errors.Join(artifactErr, transaction.Rollback())
		}
		artifacts = append(artifacts, artifact)
	}

	original := dataset
	dataset.Artifacts = artifacts
	dataset.SourceETag = version.ETag
	dataset.SourceLastModified = version.LastModified
	dataset.LastCheckedAt = &now
	dataset.UpdatedAt = &now
	if err := m.store.PutDataset(dataset); err != nil {
		rollbackErr := transaction.Rollback()
		restoreErr := m.store.PutDataset(original)
		return false, errors.Join(err, rollbackErr, restoreErr)
	}
	transaction.Commit()
	return true, nil
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
	workDir := filepath.Join(m.store.TempDir(), job.ID)
	if err := os.MkdirAll(workDir, 0o755); err != nil {
		return err
	}
	defer os.RemoveAll(workDir)

	pbfs := datasetPBFs(m.store.Root(), remaining)
	var replacementMap string
	if len(pbfs) > 0 {
		job.Stage = "rebuilding_map"
		job.Progress = 0.25
		_ = m.saveAndPublish(*job)
		var err error
		replacementMap, err = m.generateMap(ctx, workDir, pbfs)
		if err != nil {
			return err
		}
	}

	job.Stage = "deleting_files"
	job.Progress = 0.85
	_ = m.saveAndPublish(*job)
	replacements := make([]fileReplacement, 0, len(dataset.Artifacts)+1)
	seen := make(map[string]struct{}, len(dataset.Artifacts)+1)
	for _, artifact := range dataset.Artifacts {
		if artifact.Kind == model.ArtifactMap {
			continue
		}
		destination := filepath.Join(m.store.Root(), filepath.FromSlash(artifact.Path))
		if _, exists := seen[destination]; !exists {
			seen[destination] = struct{}{}
			replacements = append(replacements, fileReplacement{destination: destination})
		}
	}
	mapPath := filepath.Join(m.store.Root(), "map.pmtiles")
	if _, exists := seen[mapPath]; !exists {
		replacements = append(replacements, fileReplacement{candidate: replacementMap, destination: mapPath})
	}

	transaction, err := applyFileReplacements(workDir, replacements)
	if err != nil {
		return fmt.Errorf("stage dataset deletion: %w", err)
	}
	if err := m.store.RemoveDataset(dataset.ID); err != nil {
		return errors.Join(err, transaction.Rollback())
	}
	transaction.Commit()
	return nil
}

func (m *Manager) download(ctx context.Context, sourceURL, destination string, job *model.Job, current sourceVersion) (sourceVersion, bool, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, sourceURL, nil)
	if err != nil {
		return sourceVersion{}, false, err
	}
	req.Header.Set("User-Agent", "atlas-geodata-api/1.0")
	if current.ETag != "" {
		req.Header.Set("If-None-Match", current.ETag)
	}
	if current.LastModified != "" {
		req.Header.Set("If-Modified-Since", current.LastModified)
	}
	resp, err := m.client.Do(req)
	if err != nil {
		return sourceVersion{}, false, fmt.Errorf("download PBF: %w", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode == http.StatusNotModified {
		return current, false, nil
	}
	if resp.StatusCode != http.StatusOK {
		_, _ = io.Copy(io.Discard, resp.Body)
		return sourceVersion{}, false, fmt.Errorf("download PBF: HTTP %d", resp.StatusCode)
	}
	job.Stage = "downloading_pbf"
	_ = m.saveAndPublish(*job)
	file, err := os.Create(destination)
	if err != nil {
		return sourceVersion{}, false, err
	}
	defer file.Close()

	job.BytesTotal = resp.ContentLength
	buffer := make([]byte, 256*1024)
	lastUpdate := time.Now()
	for {
		count, readErr := resp.Body.Read(buffer)
		if count > 0 {
			if _, err := file.Write(buffer[:count]); err != nil {
				return sourceVersion{}, false, err
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
			return sourceVersion{}, false, readErr
		}
	}
	if err := file.Sync(); err != nil {
		return sourceVersion{}, false, err
	}
	return sourceVersion{
		ETag:         resp.Header.Get("ETag"),
		LastModified: resp.Header.Get("Last-Modified"),
	}, true, nil
}

func (m *Manager) buildGeocoder(ctx context.Context, pbfPath, output, countryCode string, excludeRoads bool) error {
	args := []string{"build", "--source", "openstreetmap", "--format", "pbf", "--output", output}
	if countryCode != "" {
		args = append(args, "--country", countryCode)
	}
	if excludeRoads {
		args = append(args, "--include-roads=false")
	}
	args = append(args, pbfPath)
	if err := m.runner.Run(ctx, m.cfg.PackgenBinary, args...); err != nil {
		return fmt.Errorf("build geocoder pack: %w", err)
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
		mergedHistory := stagedPBFPath(workDir, "merged-history")
		input = stagedPBFPath(workDir, "combined")
		args := append([]string{"merge", "--overwrite", "--input-format=pbf", "--output-format=pbf,history=true", "--output", mergedHistory}, pbfs...)
		if err := m.runner.Run(ctx, m.cfg.OsmiumBinary, args...); err != nil {
			return "", fmt.Errorf("merge PBF files with osmium: %w", err)
		}
		// Country extracts can overlap and may come from different snapshot times.
		// Merge preserves those versions, so collapse them before Planetiler, which
		// requires each object ID to occur only once.
		if err := m.runner.Run(ctx, m.cfg.OsmiumBinary, "time-filter", "--overwrite", "--input-format=pbf,history=true", "--output-format=pbf", "--output", input, mergedHistory); err != nil {
			return "", fmt.Errorf("deduplicate merged PBF files with osmium: %w", err)
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

// stagedPBFPath deliberately has no .pbf extension. Consumers may discover PBFs
// recursively, so incomplete files below .geodata must not look installable.
func stagedPBFPath(workDir, name string) string {
	return filepath.Join(workDir, "pbf-"+name)
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

func datasetPBFsExcept(root string, datasets []model.Dataset, excludedID string) []string {
	filtered := make([]model.Dataset, 0, len(datasets))
	for _, dataset := range datasets {
		if dataset.ID != excludedID {
			filtered = append(filtered, dataset)
		}
	}
	return datasetPBFs(root, filtered)
}

type fileReplacement struct {
	candidate   string
	destination string
}

type replacedFile struct {
	destination string
	backup      string
	hadOriginal bool
}

type fileTransaction struct {
	files []replacedFile
}

func artifactFromCandidate(kind model.ArtifactKind, relativePath, candidate string) (model.Artifact, error) {
	info, err := os.Stat(candidate)
	if err != nil {
		return model.Artifact{}, err
	}
	return model.Artifact{Kind: kind, Path: relativePath, Size: info.Size(), ModifiedAt: info.ModTime().UTC()}, nil
}

func applyFileReplacements(workDir string, replacements []fileReplacement) (*fileTransaction, error) {
	transaction := &fileTransaction{files: make([]replacedFile, 0, len(replacements))}
	for index, replacement := range replacements {
		entry := replacedFile{
			destination: replacement.destination,
			backup:      filepath.Join(workDir, fmt.Sprintf("backup-%d", index)),
		}
		if _, err := os.Stat(replacement.destination); err == nil {
			entry.hadOriginal = true
			if err := os.Rename(replacement.destination, entry.backup); err != nil {
				return nil, errors.Join(err, transaction.Rollback())
			}
		} else if !errors.Is(err, os.ErrNotExist) {
			return nil, errors.Join(err, transaction.Rollback())
		}
		transaction.files = append(transaction.files, entry)
		if replacement.candidate != "" {
			if err := os.Rename(replacement.candidate, replacement.destination); err != nil {
				return nil, errors.Join(err, transaction.Rollback())
			}
		}
	}
	return transaction, nil
}

func (transaction *fileTransaction) Rollback() error {
	var rollbackErr error
	for index := len(transaction.files) - 1; index >= 0; index-- {
		entry := transaction.files[index]
		if err := os.Remove(entry.destination); err != nil && !errors.Is(err, os.ErrNotExist) {
			rollbackErr = errors.Join(rollbackErr, err)
		}
		if entry.hadOriginal {
			if err := os.Rename(entry.backup, entry.destination); err != nil {
				rollbackErr = errors.Join(rollbackErr, err)
			}
		}
	}
	transaction.files = nil
	return rollbackErr
}

func (transaction *fileTransaction) Commit() {
	for _, entry := range transaction.files {
		if entry.hadOriginal {
			_ = os.RemoveAll(entry.backup)
		}
	}
	transaction.files = nil
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
