package origins

import (
	"context"
	"errors"
	"fmt"
	"net/url"
	"os"
	"path/filepath"
	"slices"
	"sort"
	"strings"

	"github.com/GameTec-live/atlas/apps/osManagementAPI/internal/command"
	"github.com/GameTec-live/atlas/apps/osManagementAPI/internal/ownership"
)

type Manager struct {
	path   string
	uid    int
	runner command.Runner
}

func New(path string, uid int, runner command.Runner) *Manager {
	return &Manager{path: path, uid: uid, runner: runner}
}

func (m *Manager) List() ([]string, error) {
	data, err := os.ReadFile(m.path)
	if errors.Is(err, os.ErrNotExist) {
		return []string{}, nil
	}
	if err != nil {
		return nil, fmt.Errorf("read trusted origins: %w", err)
	}
	return parse(data), nil
}

func (m *Manager) Add(ctx context.Context, origin string) ([]string, error) {
	origin, err := validate(origin)
	if err != nil {
		return nil, err
	}
	current, err := m.List()
	if err != nil {
		return nil, err
	}
	if slices.Contains(current, origin) {
		return current, nil
	}
	return m.replace(ctx, current, append(current, origin))
}

func (m *Manager) Remove(ctx context.Context, origin string) ([]string, error) {
	origin, err := validate(origin)
	if err != nil {
		return nil, err
	}
	current, err := m.List()
	if err != nil {
		return nil, err
	}
	next := slices.DeleteFunc(slices.Clone(current), func(value string) bool { return value == origin })
	if len(next) == len(current) {
		return current, nil
	}
	return m.replace(ctx, current, next)
}

func (m *Manager) replace(ctx context.Context, previous, next []string) ([]string, error) {
	sort.Strings(next)
	if err := m.write(next); err != nil {
		return nil, err
	}
	if err := m.refresh(ctx); err != nil {
		_ = m.write(previous)
		_ = m.refresh(ctx)
		return nil, err
	}
	return next, nil
}

func (m *Manager) write(origins []string) error {
	if err := os.MkdirAll(filepath.Dir(m.path), 0o700); err != nil {
		return err
	}
	temporary, err := os.CreateTemp(filepath.Dir(m.path), ".trusted-origins.*")
	if err != nil {
		return err
	}
	temporaryPath := temporary.Name()
	defer os.Remove(temporaryPath)
	if err := temporary.Chmod(0o600); err != nil {
		temporary.Close()
		return err
	}
	if len(origins) > 0 {
		if _, err := temporary.WriteString(strings.Join(origins, "\n") + "\n"); err != nil {
			temporary.Close()
			return err
		}
	}
	if err := temporary.Sync(); err != nil {
		temporary.Close()
		return err
	}
	if err := temporary.Close(); err != nil {
		return err
	}
	if err := ownership.Set(temporaryPath, m.uid, m.uid); err != nil {
		return err
	}
	return os.Rename(temporaryPath, m.path)
}

func (m *Manager) refresh(ctx context.Context) error {
	args := []string{"-u", "atlas-containers", "--", "env", "HOME=/home/atlas-containers", "XDG_RUNTIME_DIR=/run/user/2000", "/usr/local/libexec/atlas-auth-origins"}
	if _, err := m.runner.Run(ctx, "", "/usr/sbin/runuser", args...); err != nil {
		return fmt.Errorf("regenerate trusted origins: %w", err)
	}
	return nil
}

func (m *Manager) RestartAPI(ctx context.Context) error {
	args := []string{"-u", "atlas-containers", "--", "env", "HOME=/home/atlas-containers", "XDG_RUNTIME_DIR=/run/user/2000", "/usr/bin/systemctl", "--user", "try-restart", "atlas-api.service"}
	if _, err := m.runner.Run(ctx, "", "/usr/sbin/runuser", args...); err != nil {
		return fmt.Errorf("restart Atlas API: %w", err)
	}
	return nil
}

func validate(value string) (string, error) {
	parsed, err := url.Parse(strings.TrimSpace(value))
	if err != nil || parsed.Scheme != "https" || parsed.Host == "" || parsed.User != nil || (parsed.Path != "" && parsed.Path != "/") || parsed.RawQuery != "" || parsed.Fragment != "" {
		return "", fmt.Errorf("origin must be an HTTPS scheme and authority without credentials, path, query, or fragment")
	}
	if parsed.Hostname() == "" {
		return "", fmt.Errorf("origin must include a hostname")
	}
	parsed.Path = ""
	return parsed.String(), nil
}

func parse(data []byte) []string {
	result := []string{}
	for line := range strings.Lines(string(data)) {
		line = strings.TrimSpace(line)
		if line != "" && !strings.HasPrefix(line, "#") {
			result = append(result, line)
		}
	}
	return result
}
