package store

import (
	"fmt"
	"testing"
	"time"

	"github.com/GameTec-live/atlas/apps/geodataAPI/internal/model"
)

func TestJobHistoryKeepsNewestHundredCompletedJobs(t *testing.T) {
	dataStore, err := Open(t.TempDir())
	if err != nil {
		t.Fatal(err)
	}
	base := time.Date(2026, time.August, 11, 0, 0, 0, 0, time.UTC)
	for index := range 105 {
		job := model.Job{
			ID:        fmt.Sprintf("job-%03d", index),
			State:     model.JobCompleted,
			CreatedAt: base.Add(time.Duration(index) * time.Minute),
		}
		if err := dataStore.PutJob(job); err != nil {
			t.Fatal(err)
		}
	}

	jobs := dataStore.Jobs()
	if len(jobs) != 100 {
		t.Fatalf("job count = %d, want 100", len(jobs))
	}
	if jobs[0].ID != "job-104" || jobs[len(jobs)-1].ID != "job-005" {
		t.Fatalf("unexpected retained range: %s to %s", jobs[0].ID, jobs[len(jobs)-1].ID)
	}
	if _, found := dataStore.Job("job-004"); found {
		t.Fatal("old completed job was retained")
	}
}

func TestJobHistoryNeverPrunesActiveJobs(t *testing.T) {
	dataStore, err := Open(t.TempDir())
	if err != nil {
		t.Fatal(err)
	}
	base := time.Date(2026, time.August, 11, 0, 0, 0, 0, time.UTC)
	for index := range 101 {
		if err := dataStore.PutJob(model.Job{
			ID:        fmt.Sprintf("completed-%03d", index),
			State:     model.JobCompleted,
			CreatedAt: base.Add(time.Duration(index) * time.Minute),
		}); err != nil {
			t.Fatal(err)
		}
	}
	for _, state := range []model.JobState{model.JobQueued, model.JobRunning} {
		id := "active-" + string(state)
		if err := dataStore.PutJob(model.Job{ID: id, State: state, CreatedAt: base.Add(-time.Hour)}); err != nil {
			t.Fatal(err)
		}
		if _, found := dataStore.Job(id); !found {
			t.Fatalf("%s job was pruned", state)
		}
	}

	if jobs := dataStore.Jobs(); len(jobs) != 102 {
		t.Fatalf("job count = %d, want 100 completed plus 2 active", len(jobs))
	}
}
