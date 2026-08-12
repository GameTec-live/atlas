package store

import (
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"sync"
	"time"

	"github.com/GameTec-live/atlas/apps/geodataAPI/internal/model"
)

type Store struct {
	root      string
	statePath string
	mu        sync.RWMutex
	state     model.State
}

const maxJobHistory = 100

func Open(root string) (*Store, error) {
	for _, directory := range []string{root, filepath.Join(root, ".geodata"), filepath.Join(root, ".geodata", "tmp")} {
		if err := os.MkdirAll(directory, 0o755); err != nil {
			return nil, fmt.Errorf("create %s: %w", directory, err)
		}
	}
	s := &Store{
		root:      root,
		statePath: filepath.Join(root, ".geodata", "state.json"),
		state: model.State{
			Version:  1,
			Datasets: make(map[string]model.Dataset),
			Jobs:     make(map[string]model.Job),
		},
	}
	if err := s.load(); err != nil {
		return nil, err
	}
	return s, nil
}

func (s *Store) Root() string    { return s.root }
func (s *Store) TempDir() string { return filepath.Join(s.root, ".geodata", "tmp") }

func (s *Store) DiskSpace() (model.DiskSpace, error) {
	return diskSpace(s.root)
}

func (s *Store) Datasets() []model.Dataset {
	s.mu.RLock()
	defer s.mu.RUnlock()
	result := make([]model.Dataset, 0, len(s.state.Datasets))
	for _, dataset := range s.state.Datasets {
		dataset.Artifacts = s.refreshArtifacts(dataset.Artifacts)
		if len(dataset.Artifacts) == len(s.state.Datasets[dataset.ID].Artifacts) {
			dataset.State = "ready"
		} else {
			dataset.State = "degraded"
		}
		result = append(result, dataset)
	}
	sort.Slice(result, func(i, j int) bool { return result[i].InstalledAt.After(result[j].InstalledAt) })
	return result
}

func (s *Store) Dataset(id string) (model.Dataset, bool) {
	s.mu.RLock()
	defer s.mu.RUnlock()
	dataset, ok := s.state.Datasets[id]
	return dataset, ok
}

func (s *Store) PutDataset(dataset model.Dataset) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	previous, existed := s.state.Datasets[dataset.ID]
	s.state.Datasets[dataset.ID] = dataset
	if err := s.saveLocked(); err != nil {
		if existed {
			s.state.Datasets[dataset.ID] = previous
		} else {
			delete(s.state.Datasets, dataset.ID)
		}
		return err
	}
	return nil
}

func (s *Store) RemoveDataset(id string) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	previous, existed := s.state.Datasets[id]
	delete(s.state.Datasets, id)
	if err := s.saveLocked(); err != nil {
		if existed {
			s.state.Datasets[id] = previous
		}
		return err
	}
	return nil
}

func (s *Store) Jobs() []model.Job {
	s.mu.RLock()
	defer s.mu.RUnlock()
	result := make([]model.Job, 0, len(s.state.Jobs))
	for _, job := range s.state.Jobs {
		result = append(result, job)
	}
	sort.Slice(result, func(i, j int) bool { return result[i].CreatedAt.After(result[j].CreatedAt) })
	return result
}

func (s *Store) Job(id string) (model.Job, bool) {
	s.mu.RLock()
	defer s.mu.RUnlock()
	job, ok := s.state.Jobs[id]
	return job, ok
}

func (s *Store) PutJob(job model.Job) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.state.Jobs[job.ID] = job
	s.pruneJobHistoryLocked()
	return s.saveLocked()
}

func (s *Store) MarkInterruptedJobs() error {
	s.mu.Lock()
	defer s.mu.Unlock()
	changed := false
	finished := time.Now().UTC()
	for id, job := range s.state.Jobs {
		if job.State == model.JobQueued || job.State == model.JobRunning {
			job.State = model.JobFailed
			job.Stage = "interrupted"
			job.Error = "service stopped before the job completed"
			job.FinishedAt = &finished
			s.state.Jobs[id] = job
			changed = true
		}
	}
	if s.pruneJobHistoryLocked() {
		changed = true
	}
	if !changed {
		return nil
	}
	return s.saveLocked()
}

func (s *Store) pruneJobHistoryLocked() bool {
	completed := make([]model.Job, 0, len(s.state.Jobs))
	for _, job := range s.state.Jobs {
		if job.State != model.JobQueued && job.State != model.JobRunning {
			completed = append(completed, job)
		}
	}
	if len(completed) <= maxJobHistory {
		return false
	}
	sort.Slice(completed, func(i, j int) bool {
		if completed[i].CreatedAt.Equal(completed[j].CreatedAt) {
			return completed[i].ID > completed[j].ID
		}
		return completed[i].CreatedAt.After(completed[j].CreatedAt)
	})
	for _, job := range completed[maxJobHistory:] {
		delete(s.state.Jobs, job.ID)
	}
	return true
}

func (s *Store) Artifact(kind model.ArtifactKind, relativePath string) (model.Artifact, error) {
	info, err := os.Stat(filepath.Join(s.root, filepath.FromSlash(relativePath)))
	if err != nil {
		return model.Artifact{}, err
	}
	return model.Artifact{Kind: kind, Path: relativePath, Size: info.Size(), ModifiedAt: info.ModTime().UTC()}, nil
}

func (s *Store) load() error {
	content, err := os.ReadFile(s.statePath)
	if errors.Is(err, os.ErrNotExist) {
		return nil
	}
	if err != nil {
		return fmt.Errorf("read state: %w", err)
	}
	if err := json.Unmarshal(content, &s.state); err != nil {
		return fmt.Errorf("decode state: %w", err)
	}
	if s.state.Datasets == nil {
		s.state.Datasets = make(map[string]model.Dataset)
	}
	if s.state.Jobs == nil {
		s.state.Jobs = make(map[string]model.Job)
	}
	return s.MarkInterruptedJobs()
}

func (s *Store) saveLocked() error {
	content, err := json.MarshalIndent(s.state, "", "  ")
	if err != nil {
		return fmt.Errorf("encode state: %w", err)
	}
	temporary := s.statePath + ".tmp"
	if err := os.WriteFile(temporary, append(content, '\n'), 0o644); err != nil {
		return fmt.Errorf("write state: %w", err)
	}
	if err := replaceFile(temporary, s.statePath); err != nil {
		return fmt.Errorf("commit state: %w", err)
	}
	return nil
}

func (s *Store) refreshArtifacts(artifacts []model.Artifact) []model.Artifact {
	result := make([]model.Artifact, 0, len(artifacts))
	for _, artifact := range artifacts {
		info, err := os.Stat(filepath.Join(s.root, filepath.FromSlash(artifact.Path)))
		if err != nil {
			continue
		}
		artifact.Size = info.Size()
		artifact.ModifiedAt = info.ModTime().UTC()
		result = append(result, artifact)
	}
	return result
}

func replaceFile(source, destination string) error {
	if err := os.Rename(source, destination); err == nil {
		return nil
	}
	// Windows cannot rename over an existing file. State remains recoverable via
	// the temporary file if the second rename is interrupted.
	if err := os.Remove(destination); err != nil && !errors.Is(err, os.ErrNotExist) {
		return err
	}
	return os.Rename(source, destination)
}
