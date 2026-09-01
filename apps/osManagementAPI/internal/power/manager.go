package power

import (
	"context"

	"github.com/GameTec-live/atlas/apps/osManagementAPI/internal/command"
)

type Manager struct {
	runner command.Runner
}

func New(runner command.Runner) *Manager {
	return &Manager{runner: runner}
}

func (m *Manager) Reboot(ctx context.Context) error {
	_, err := m.runner.Run(ctx, "", "/usr/bin/systemctl", "reboot")
	return err
}

func (m *Manager) RebootTryboot(ctx context.Context) error {
	_, err := m.runner.Run(ctx, "", "/usr/sbin/reboot", "0 tryboot")
	return err
}

func (m *Manager) Poweroff(ctx context.Context) error {
	_, err := m.runner.Run(ctx, "", "/usr/bin/systemctl", "poweroff")
	return err
}
