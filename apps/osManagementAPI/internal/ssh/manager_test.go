package ssh

import (
	"context"
	"errors"
	"slices"
	"testing"
)

type call struct {
	name string
	args []string
}

type fakeRunner struct {
	output string
	err    error
	calls  []call
}

func (r *fakeRunner) Run(_ context.Context, _ string, name string, args ...string) (string, error) {
	r.calls = append(r.calls, call{name: name, args: slices.Clone(args)})
	return r.output, r.err
}

func TestStatus(t *testing.T) {
	for _, test := range []struct {
		state   string
		enabled bool
	}{
		{state: "enabled", enabled: true},
		{state: "disabled", enabled: false},
	} {
		t.Run(test.state, func(t *testing.T) {
			runner := &fakeRunner{output: test.state}
			enabled, err := New(runner).Status(context.Background())
			if err != nil {
				t.Fatal(err)
			}
			if enabled != test.enabled {
				t.Fatalf("enabled=%v, want %v", enabled, test.enabled)
			}
			assertCall(t, runner.calls, "status")
		})
	}
}

func TestStatusRejectsUnknownState(t *testing.T) {
	_, err := New(&fakeRunner{output: "maybe"}).Status(context.Background())
	if err == nil {
		t.Fatal("expected unknown state to fail")
	}
}

func TestEnableAndDisableDelegateToController(t *testing.T) {
	runner := &fakeRunner{}
	manager := New(runner)
	if err := manager.Enable(context.Background()); err != nil {
		t.Fatal(err)
	}
	if err := manager.Disable(context.Background()); err != nil {
		t.Fatal(err)
	}
	if len(runner.calls) != 2 {
		t.Fatalf("got %d calls, want 2", len(runner.calls))
	}
	assertCall(t, runner.calls[:1], "enable")
	assertCall(t, runner.calls[1:], "disable")
}

func TestControllerErrorIsReturned(t *testing.T) {
	want := errors.New("controller failed")
	err := New(&fakeRunner{err: want}).Enable(context.Background())
	if !errors.Is(err, want) {
		t.Fatalf("got %v, want %v", err, want)
	}
}

func assertCall(t *testing.T, calls []call, argument string) {
	t.Helper()
	if len(calls) != 1 || calls[0].name != controller || !slices.Equal(calls[0].args, []string{argument}) {
		t.Fatalf("unexpected calls: %#v", calls)
	}
}
