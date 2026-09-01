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
	timedatectl  = "/usr/bin/timedatectl"
	zoneinfoRoot = "/usr/share/zoneinfo"
)

type Manager struct {
	runner       command.Runner
	zoneinfoRoot string
}

type Request struct {
	Timezone string `json:"timezone"`
}

func New(runner command.Runner) *Manager {
	return &Manager{runner: runner, zoneinfoRoot: zoneinfoRoot}
}

func (m *Manager) Status(ctx context.Context) (string, error) {
	value, err := m.runner.Run(ctx, "", timedatectl, "show", "--property=Timezone", "--value")
	if err != nil {
		return "", fmt.Errorf("read system timezone: %w", err)
	}
	if value == "" {
		return "", errors.New("system timezone is empty")
	}
	if err := m.validate(value); err != nil {
		return "", fmt.Errorf("system returned an invalid timezone: %w", err)
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
	if _, err := m.runner.Run(ctx, "", timedatectl, "set-timezone", value); err != nil {
		return fmt.Errorf("set system timezone: %w", err)
	}
	if err := m.setDatabase(ctx, value); err == nil {
		return nil
	} else {
		setErr := err
		_, systemRollbackErr := m.runner.Run(ctx, "", timedatectl, "set-timezone", previous)
		databaseRollbackErr := m.setDatabase(ctx, previous)
		return errors.Join(
			fmt.Errorf("set database timezone: %w", setErr),
			wrapRollbackError("system", systemRollbackErr),
			wrapRollbackError("database", databaseRollbackErr),
		)
	}
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
