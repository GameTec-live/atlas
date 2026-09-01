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
	timezone     string
	databaseErr  error
	calls        []call
	databaseRuns int
}

func (r *fakeRunner) Run(_ context.Context, _ string, name string, args ...string) (string, error) {
	r.calls = append(r.calls, call{name: name, args: slices.Clone(args)})
	if name == timedatectl && slices.Contains(args, "show") {
		return r.timezone, nil
	}
	if name == timedatectl && slices.Contains(args, "set-timezone") {
		r.timezone = args[len(args)-1]
		return "", nil
	}
	if name == "/usr/sbin/runuser" {
		r.databaseRuns++
		if r.databaseRuns == 1 && r.databaseErr != nil {
			return "", r.databaseErr
		}
	}
	return "", nil
}

func TestStatusAndSetUpdateSystemAndDatabase(t *testing.T) {
	runner := &fakeRunner{timezone: "Etc/UTC"}
	manager := testManager(t, runner, "Etc/UTC", "Europe/Vienna")

	status, err := manager.Status(context.Background())
	if err != nil || status != "Etc/UTC" {
		t.Fatalf("unexpected status: %q, %v", status, err)
	}
	if err := manager.Set(context.Background(), "Europe/Vienna"); err != nil {
		t.Fatal(err)
	}
	if runner.timezone != "Europe/Vienna" {
		t.Fatalf("system timezone=%q, want Europe/Vienna", runner.timezone)
	}
	if !slices.ContainsFunc(runner.calls, func(call call) bool {
		arguments := strings.Join(call.args, " ")
		return call.name == "/usr/sbin/runuser" &&
			strings.Contains(arguments, "/usr/bin/systemd-run --user --wait --collect --quiet --pipe --unit=atlas-db-timezone") &&
			strings.Contains(arguments, "/usr/bin/podman exec atlas-db psql") &&
			strings.Contains(arguments, "ALTER SYSTEM SET timezone TO 'Europe/Vienna'") &&
			strings.Contains(arguments, "ALTER SYSTEM SET log_timezone TO 'Europe/Vienna'") &&
			strings.Contains(arguments, "SELECT pg_reload_conf()")
	}) {
		t.Fatalf("database timezone was not updated: %#v", runner.calls)
	}
}

func TestSetRejectsUnknownOrUnsafeTimezone(t *testing.T) {
	runner := &fakeRunner{timezone: "Etc/UTC"}
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
	runner := &fakeRunner{timezone: "Etc/UTC", databaseErr: errors.New("database unavailable")}
	manager := testManager(t, runner, "Etc/UTC", "Europe/Vienna")

	err := manager.Set(context.Background(), "Europe/Vienna")
	if err == nil || !strings.Contains(err.Error(), "set database timezone") {
		t.Fatalf("unexpected error: %v", err)
	}
	if runner.timezone != "Etc/UTC" || runner.databaseRuns != 2 {
		t.Fatalf("rollback incomplete: timezone=%q database runs=%d", runner.timezone, runner.databaseRuns)
	}
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
	return &Manager{runner: runner, zoneinfoRoot: root}
}
