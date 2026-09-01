package timezone

import (
	"context"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"github.com/GameTec-live/atlas/apps/osManagementAPI/internal/command"
)

const (
	zoneinfoRoot = "/usr/share/zoneinfo"
)

type Manager struct {
	runner       command.Runner
	zoneinfoRoot string
	statePath    string
}

type Request struct {
	Timezone string `json:"timezone"`
}

func New(statePath string, runner command.Runner) *Manager {
	return &Manager{runner: runner, zoneinfoRoot: zoneinfoRoot, statePath: statePath}
}

func (m *Manager) Status(context.Context) (string, error) {
	target, err := os.Readlink(m.statePath)
	if err != nil {
		return "", fmt.Errorf("read persistent system timezone: %w", err)
	}
	relative, err := filepath.Rel(m.zoneinfoRoot, target)
	if err != nil {
		return "", fmt.Errorf("resolve system timezone: %w", err)
	}
	value := filepath.ToSlash(relative)
	if err := m.validate(value); err != nil {
		return "", fmt.Errorf("persistent system timezone is invalid: %w", err)
	}
	return value, nil
}

func (m *Manager) Set(ctx context.Context, value string) error {
	if err := m.validate(value); err != nil {
		return err
	}
	previous, err := m.Status(ctx)
	if err != nil {
		return err
	}
	if err := m.setSystem(value); err != nil {
		return fmt.Errorf("set system timezone: %w", err)
	}
	if err := m.setDatabase(ctx, value); err == nil {
		return nil
	} else {
		setErr := err
		systemRollbackErr := m.setSystem(previous)
		databaseRollbackErr := m.setDatabase(ctx, previous)
		return errors.Join(
			fmt.Errorf("set database timezone: %w", setErr),
			wrapRollbackError("system", systemRollbackErr),
			wrapRollbackError("database", databaseRollbackErr),
		)
	}
}

// /etc/localtime is immutable on Atlas OS and points at this persistent
// symlink. Replacing its target changes the host timezone without writing the
// EROFS system slot and keeps the setting across A/B updates.
func (m *Manager) setSystem(value string) error {
	stateDir := filepath.Dir(m.statePath)
	if err := os.MkdirAll(stateDir, 0o700); err != nil {
		return err
	}
	temporary, err := os.CreateTemp(stateDir, ".localtime.*")
	if err != nil {
		return err
	}
	temporaryPath := temporary.Name()
	if err := temporary.Close(); err != nil {
		return err
	}
	defer os.Remove(temporaryPath)
	if err := os.Remove(temporaryPath); err != nil {
		return err
	}
	if err := os.Symlink(filepath.Join(m.zoneinfoRoot, filepath.FromSlash(value)), temporaryPath); err != nil {
		return err
	}
	if err := os.Rename(temporaryPath, m.statePath); err != nil {
		return err
	}
	return syncDirectory(stateDir)
}

func (m *Manager) validate(value string) error {
	if value == "" || strings.TrimSpace(value) != value || strings.HasPrefix(value, "/") {
		return errors.New("timezone must be an IANA timezone name")
	}
	for _, character := range value {
		if (character >= 'a' && character <= 'z') || (character >= 'A' && character <= 'Z') ||
			(character >= '0' && character <= '9') || strings.ContainsRune("/_+-", character) {
			continue
		}
		return errors.New("timezone must be an IANA timezone name")
	}
	for segment := range strings.SplitSeq(value, "/") {
		if segment == "" || segment == "." || segment == ".." {
			return errors.New("timezone must be an IANA timezone name")
		}
	}
	info, err := os.Stat(filepath.Join(m.zoneinfoRoot, filepath.FromSlash(value)))
	if err != nil || !info.Mode().IsRegular() {
		return fmt.Errorf("unknown timezone %q", value)
	}
	return nil
}

func (m *Manager) setDatabase(ctx context.Context, value string) error {
	timezoneQuery := "ALTER SYSTEM SET timezone TO '" + value + "'"
	logTimezoneQuery := "ALTER SYSTEM SET log_timezone TO '" + value + "'"
	args := []string{
		"-u", "atlas-containers", "--", "env",
		"HOME=/home/atlas-containers", "XDG_RUNTIME_DIR=/run/user/2000",
		"/usr/bin/systemd-run", "--user", "--wait", "--collect", "--quiet", "--pipe", "--unit=atlas-db-timezone",
		"/usr/bin/podman", "exec", "atlas-db", "psql",
		"--username=atlas", "--dbname=atlas", "--no-psqlrc", "--set=ON_ERROR_STOP=1",
		"--command", timezoneQuery, "--command", logTimezoneQuery, "--command", "SELECT pg_reload_conf()",
	}
	_, err := m.runner.Run(ctx, "", "/usr/sbin/runuser", args...)
	return err
}

func wrapRollbackError(target string, err error) error {
	if err == nil {
		return nil
	}
	return fmt.Errorf("roll back %s timezone: %w", target, err)
}
