package update

import (
	"context"
	"fmt"
	"strings"
	"sync"

	"github.com/GameTec-live/atlas/apps/osManagementAPI/internal/command"
)

const updateCommand = "/usr/local/sbin/atlas-ab-update"

type Status struct {
	Active  string `json:"active"`
	Other   string `json:"other"`
	Pending string `json:"pending,omitempty"`
}

func (s Status) IsCandidate() bool {
	return s.Pending != "" && s.Active == s.Pending
}

type Manager struct {
	runner            command.Runner
	mu                sync.Mutex
	rollbackRequested bool
}

func New(runner command.Runner) *Manager {
	return &Manager{runner: runner}
}

func (m *Manager) Status(ctx context.Context) (Status, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	output, err := m.runner.Run(ctx, "", updateCommand, "status")
	if err != nil {
		return Status{}, err
	}
	status := Status{}
	for line := range strings.Lines(output) {
		key, value, ok := strings.Cut(strings.TrimSpace(line), "=")
		if !ok {
			continue
		}
		value, _, _ = strings.Cut(value, " ")
		switch key {
		case "active":
			status.Active = value
		case "other":
			status.Other = value
		case "pending":
			if value != "none" {
				status.Pending = value
			}
		}
	}
	if status.Active == "" || status.Other == "" {
		return Status{}, fmt.Errorf("invalid atlas-ab-update status output")
	}
	return status, nil
}

func (m *Manager) Apply(ctx context.Context, bundlePath string) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.rollbackRequested = false
	_, err := m.runner.Run(ctx, "", updateCommand, "install", bundlePath)
	return err
}

func (m *Manager) Commit(ctx context.Context) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	if m.rollbackRequested {
		return fmt.Errorf("rollback was requested; refusing to commit")
	}
	_, err := m.runner.Run(ctx, "", updateCommand, "commit")
	return err
}

func (m *Manager) Rollback(ctx context.Context) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.rollbackRequested = true
	_, err := m.runner.Run(ctx, "", updateCommand, "rollback")
	return err
}
