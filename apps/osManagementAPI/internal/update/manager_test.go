package update

import (
	"context"
	"sync"
	"testing"
	"time"
)

type fakeRunner struct {
	mu      sync.Mutex
	outputs []string
	calls   [][]string
}

func (r *fakeRunner) Run(_ context.Context, _ string, name string, args ...string) (string, error) {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.calls = append(r.calls, append([]string{name}, args...))
	if len(r.outputs) == 0 {
		return "", nil
	}
	output := r.outputs[0]
	r.outputs = r.outputs[1:]
	return output, nil
}

func TestStatusParsesSlotLabels(t *testing.T) {
	runner := &fakeRunner{outputs: []string{"active=system_b (/dev/mmcblk0p5)\nother=system_a (/dev/mmcblk0p3)\npending=system_b\n"}}
	manager := New(runner)
	status, err := manager.Status(context.Background())
	if err != nil {
		t.Fatal(err)
	}
	if status.Active != "system_b" || status.Other != "system_a" || status.Pending != "system_b" || !status.IsCandidate() {
		t.Fatalf("unexpected status: %+v", status)
	}
}

type sequenceHealth struct {
	mu      sync.Mutex
	results []bool
}

func (h *sequenceHealth) Healthy(context.Context) (bool, string) {
	h.mu.Lock()
	defer h.mu.Unlock()
	if len(h.results) == 0 {
		return true, ""
	}
	result := h.results[0]
	h.results = h.results[1:]
	if !result {
		return false, "container unhealthy"
	}
	return true, ""
}

func TestMonitorRequiresContinuousHealthyWindow(t *testing.T) {
	runner := &fakeRunner{outputs: []string{
		"active=system_b (/dev/b)\nother=system_a (/dev/a)\npending=system_b\n",
		"",
	}}
	manager := New(runner)
	monitor := NewMonitor(manager, &sequenceHealth{results: []bool{true, false, true}}, 20*time.Millisecond, 5*time.Millisecond)
	ctx, cancel := context.WithTimeout(context.Background(), time.Second)
	defer cancel()
	monitor.Run(ctx)
	if phase := monitor.Status().Phase; phase != "committed" {
		t.Fatalf("expected committed monitor, got %q", phase)
	}
	runner.mu.Lock()
	defer runner.mu.Unlock()
	if len(runner.calls) != 2 || runner.calls[1][1] != "commit" {
		t.Fatalf("expected one commit after status, calls: %#v", runner.calls)
	}
}

func TestStatusRejectsMalformedOutput(t *testing.T) {
	manager := New(&fakeRunner{outputs: []string{"pending=none"}})
	_, err := manager.Status(context.Background())
	if err == nil {
		t.Fatal("expected malformed status error")
	}
}

func TestRollbackRequestPreventsAutomaticCommit(t *testing.T) {
	runner := &fakeRunner{}
	manager := New(runner)
	if err := manager.Rollback(context.Background()); err != nil {
		t.Fatal(err)
	}
	if err := manager.Commit(context.Background()); err == nil {
		t.Fatal("commit should be refused after a rollback request")
	}
	runner.mu.Lock()
	defer runner.mu.Unlock()
	if len(runner.calls) != 1 || runner.calls[0][1] != "rollback" {
		t.Fatalf("unexpected commands after rollback: %#v", runner.calls)
	}
}
