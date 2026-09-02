package timezone

import (
	"context"
	"errors"
	"os"
	"path/filepath"
	"slices"
	"strings"
	"testing"
)

type call struct {
	name string
	args []string
}

type fakeRunner struct {
	databaseErr           error
	cancelOnFirstDatabase func()
	calls                 []call
	databaseContextErrors []error
	databaseHasDeadlines  []bool
	databaseRuns          int
}

func (r *fakeRunner) Run(ctx context.Context, _ string, name string, args ...string) (string, error) {
	r.calls = append(r.calls, call{name: name, args: slices.Clone(args)})
	if name == "/usr/sbin/runuser" {
		r.databaseRuns++
		if r.databaseRuns == 1 && r.cancelOnFirstDatabase != nil {
			r.cancelOnFirstDatabase()
		}
		r.databaseContextErrors = append(r.databaseContextErrors, ctx.Err())
		_, hasDeadline := ctx.Deadline()
		r.databaseHasDeadlines = append(r.databaseHasDeadlines, hasDeadline)
		if r.databaseRuns == 1 && r.databaseErr != nil {
			return "", r.databaseErr
		}
	}
	return "", nil
}

func TestStatusAndSetUpdateSystemAndDatabase(t *testing.T) {
	runner := &fakeRunner{}
	manager := testManager(t, runner, "Etc/UTC", "Europe/Vienna")

	status, err := manager.Status(context.Background())
	if err != nil || status != "Etc/UTC" {
		t.Fatalf("unexpected status: %q, %v", status, err)
	}
	if err := manager.Set(context.Background(), "Europe/Vienna"); err != nil {
		t.Fatal(err)
	}
	status, err = manager.Status(context.Background())
	if err != nil || status != "Europe/Vienna" {
		t.Fatalf("system timezone=%q, want Europe/Vienna: %v", status, err)
	}
	if !slices.ContainsFunc(runner.calls, func(call call) bool {
		arguments := strings.Join(call.args, " ")
		return call.name == "/usr/sbin/runuser" &&
			strings.Contains(arguments, "/usr/bin/systemd-run --user --wait --collect --quiet --pipe --unit=atlas-db-timezone-") &&
			strings.Contains(arguments, "/usr/bin/podman exec atlas-db psql") &&
			strings.Contains(arguments, "ALTER SYSTEM SET timezone TO 'Europe/Vienna'") &&
			strings.Contains(arguments, "ALTER SYSTEM SET log_timezone TO 'Europe/Vienna'") &&
			strings.Contains(arguments, "SELECT pg_reload_conf()")
	}) {
		t.Fatalf("database timezone was not updated: %#v", runner.calls)
	}
}

func TestSetRejectsUnknownOrUnsafeTimezone(t *testing.T) {
	runner := &fakeRunner{}
	manager := testManager(t, runner, "Etc/UTC")
	for _, value := range []string{"", "Europe/Unknown", "../Etc/UTC", "Europe/Vienna'"} {
		t.Run(value, func(t *testing.T) {
			if err := manager.Set(context.Background(), value); err == nil {
				t.Fatal("expected invalid timezone to fail")
			}
		})
	}
	if len(runner.calls) != 0 {
		t.Fatalf("invalid values invoked commands: %#v", runner.calls)
	}
}

func TestDatabaseFailureRollsBackSystemAndDatabase(t *testing.T) {
	databaseErr := errors.New("database unavailable")
	requestCtx, cancelRequest := context.WithCancel(context.Background())
	runner := &fakeRunner{databaseErr: databaseErr, cancelOnFirstDatabase: cancelRequest}
	manager := testManager(t, runner, "Etc/UTC", "Europe/Vienna")

	err := manager.Set(requestCtx, "Europe/Vienna")
	if err == nil || !strings.Contains(err.Error(), "set database timezone") || !errors.Is(err, databaseErr) {
		t.Fatalf("unexpected error: %v", err)
	}
	status, statusErr := manager.Status(context.Background())
	if statusErr != nil || status != "Etc/UTC" || runner.databaseRuns != 2 {
		t.Fatalf("rollback incomplete: timezone=%q database runs=%d error=%v", status, runner.databaseRuns, statusErr)
	}
	if !errors.Is(runner.databaseContextErrors[0], context.Canceled) {
		t.Fatalf("initial database context error=%v, want canceled", runner.databaseContextErrors[0])
	}
	if runner.databaseContextErrors[1] != nil || !runner.databaseHasDeadlines[1] {
		t.Fatalf("rollback reused canceled context: error=%v has deadline=%v", runner.databaseContextErrors[1], runner.databaseHasDeadlines[1])
	}
	forwardUnit := databaseUnit(t, runner.calls[0])
	rollbackUnit := databaseUnit(t, runner.calls[1])
	if forwardUnit == rollbackUnit {
		t.Fatalf("rollback reused transient unit %q", forwardUnit)
	}
}

func databaseUnit(t *testing.T, call call) string {
	t.Helper()
	for _, argument := range call.args {
		if unit, ok := strings.CutPrefix(argument, "--unit="); ok {
			if !strings.HasPrefix(unit, "atlas-db-timezone-") {
				t.Fatalf("unexpected database unit %q", unit)
			}
			return unit
		}
	}
	t.Fatalf("database call has no transient unit: %#v", call)
	return ""
}

func testManager(t *testing.T, runner *fakeRunner, zones ...string) *Manager {
	t.Helper()
	root := t.TempDir()
	for _, zone := range zones {
		path := filepath.Join(root, filepath.FromSlash(zone))
		if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
			t.Fatal(err)
		}
		if err := os.WriteFile(path, []byte("zoneinfo"), 0o644); err != nil {
			t.Fatal(err)
		}
	}
	stateDir := t.TempDir()
	statePath := filepath.Join(stateDir, "localtime")
	if err := os.Symlink(filepath.Join(root, filepath.FromSlash(zones[0])), statePath); err != nil {
		t.Fatal(err)
	}
	return &Manager{runner: runner, zoneinfoRoot: root, statePath: statePath}
}
