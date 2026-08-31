package ssh

import (
	"context"
	"fmt"

	"github.com/GameTec-live/atlas/apps/osManagementAPI/internal/command"
)

const controller = "/usr/local/sbin/atlas-ssh"

type Manager struct {
	runner command.Runner
}

func New(runner command.Runner) *Manager {
	return &Manager{runner: runner}
}

func (m *Manager) Status(ctx context.Context) (bool, error) {
	state, err := m.runner.Run(ctx, "", controller, "status")
	if err != nil {
		return false, err
	}
	switch state {
	case "enabled":
		return true, nil
	case "disabled":
		return false, nil
	default:
		return false, fmt.Errorf("unexpected SSH state %q", state)
	}
}

func (m *Manager) Enable(ctx context.Context) error {
	_, err := m.runner.Run(ctx, "", controller, "enable")
	return err
}

func (m *Manager) Disable(ctx context.Context) error {
	_, err := m.runner.Run(ctx, "", controller, "disable")
	return err
}
