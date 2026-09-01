package remoteaccess

import (
	"context"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"github.com/GameTec-live/atlas/apps/osManagementAPI/internal/command"
	"github.com/GameTec-live/atlas/apps/osManagementAPI/internal/ownership"
)

const (
	cloudflareConfig = "cloudflare-tunnel.env"
	cloudflareUnit   = "atlas-cloudflare-tunnel.service"
	tailscaleConfig  = "tailscale.env"
	tailscaleVolume  = "atlas-tailscale-state-volume.service"
	tailscaleUnit    = "atlas-tailscale.service"
)

type ProviderStatus struct {
	Provisioned bool   `json:"provisioned"`
	State       string `json:"state"`
	Detail      string `json:"detail,omitempty"`
}

type Status struct {
	CloudflareTunnel ProviderStatus `json:"cloudflareTunnel"`
	Tailscale        ProviderStatus `json:"tailscale"`
}

type CloudflareRequest struct {
	Token string `json:"token"`
}

type TailscaleRequest struct {
	AuthKey  string `json:"authKey"`
	Hostname string `json:"hostname,omitempty"`
}

// Manager persists only provider credentials. Immutable Quadlets define how
// the connectors run, keeping privileged API input out of container arguments.
type Manager struct {
	configDir string
	uid       int
	runner    command.Runner
}

func New(configDir string, uid int, runner command.Runner) *Manager {
	return &Manager{configDir: configDir, uid: uid, runner: runner}
}

func (m *Manager) Status(ctx context.Context) (Status, error) {
	cloudflare, err := m.providerStatus(ctx, cloudflareConfig, cloudflareUnit)
	if err != nil {
		return Status{}, fmt.Errorf("read Cloudflare Tunnel status: %w", err)
	}
	tailscale, err := m.providerStatus(ctx, tailscaleConfig, tailscaleUnit)
	if err != nil {
		return Status{}, fmt.Errorf("read Tailscale status: %w", err)
	}
	return Status{CloudflareTunnel: cloudflare, Tailscale: tailscale}, nil
}

func (m *Manager) ProvisionCloudflare(ctx context.Context, request CloudflareRequest) error {
	if err := validateToken("token", request.Token); err != nil {
		return err
	}
	return m.provision(ctx, cloudflareConfig, cloudflareUnit, "TUNNEL_TOKEN="+request.Token+"\n")
}

func (m *Manager) RemoveCloudflare(ctx context.Context) error {
	return m.remove(ctx, cloudflareConfig, cloudflareUnit)
}

func (m *Manager) ProvisionTailscale(ctx context.Context, request TailscaleRequest) error {
	if err := validateToken("authKey", request.AuthKey); err != nil {
		return err
	}
	if err := validateHostname(request.Hostname); err != nil {
		return err
	}
	config := "TS_AUTHKEY=" + request.AuthKey + "\n"
	if request.Hostname != "" {
		config += "TS_HOSTNAME=" + request.Hostname + "\n"
	}
	return m.provision(ctx, tailscaleConfig, tailscaleUnit, config)
}

func (m *Manager) RemoveTailscale(ctx context.Context) error {
	if err := m.remove(ctx, tailscaleConfig, tailscaleUnit); err != nil {
		return err
	}
	if _, err := m.systemctl(ctx, "stop", tailscaleVolume); err != nil {
		return fmt.Errorf("stop Tailscale state volume: %w", err)
	}
	if _, err := m.podman(ctx, "volume", "rm", "--force", "--ignore", "atlas-tailscale-state"); err != nil {
		return fmt.Errorf("remove Tailscale state: %w", err)
	}
	return nil
}

func (m *Manager) providerStatus(ctx context.Context, configName, unit string) (ProviderStatus, error) {
	_, err := os.Stat(filepath.Join(m.configDir, configName))
	if errors.Is(err, os.ErrNotExist) {
		return ProviderStatus{State: "not_provisioned"}, nil
	}
	if err != nil {
		return ProviderStatus{}, err
	}

	output, err := m.systemctl(ctx, "show", "--property=LoadState", "--property=ActiveState", "--property=SubState", unit)
	if err != nil {
		return ProviderStatus{}, err
	}
	properties := map[string]string{}
	for line := range strings.Lines(output) {
		key, value, ok := strings.Cut(strings.TrimSpace(line), "=")
		if ok {
			properties[key] = value
		}
	}
	if properties["LoadState"] == "not-found" {
		return ProviderStatus{Provisioned: true, State: "unavailable"}, nil
	}
	state := properties["ActiveState"]
	if state == "" {
		state = "unknown"
	}
	return ProviderStatus{Provisioned: true, State: state, Detail: properties["SubState"]}, nil
}

func (m *Manager) provision(ctx context.Context, configName, unit, content string) error {
	if err := m.writeConfig(configName, content); err != nil {
		return err
	}
	if _, err := m.systemctl(ctx, "daemon-reload"); err != nil {
		return fmt.Errorf("reload rootless services: %w", err)
	}
	if _, err := m.systemctl(ctx, "restart", unit); err != nil {
		return fmt.Errorf("start %s: %w", unit, err)
	}
	return nil
}

func (m *Manager) remove(ctx context.Context, configName, unit string) error {
	if _, err := m.systemctl(ctx, "stop", unit); err != nil {
		return fmt.Errorf("stop %s: %w", unit, err)
	}
	if err := os.Remove(filepath.Join(m.configDir, configName)); err != nil && !errors.Is(err, os.ErrNotExist) {
		return fmt.Errorf("remove provider configuration: %w", err)
	}
	return nil
}

func (m *Manager) writeConfig(name, content string) error {
	if err := os.MkdirAll(m.configDir, 0o700); err != nil {
		return err
	}
	temporary, err := os.CreateTemp(m.configDir, "."+name+".*")
	if err != nil {
		return err
	}
	temporaryPath := temporary.Name()
	defer os.Remove(temporaryPath)
	if err := temporary.Chmod(0o600); err != nil {
		temporary.Close()
		return err
	}
	if _, err := temporary.WriteString(content); err != nil {
		temporary.Close()
		return err
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
	return os.Rename(temporaryPath, filepath.Join(m.configDir, name))
}

func (m *Manager) systemctl(ctx context.Context, args ...string) (string, error) {
	prefix := []string{"-u", "atlas-containers", "--", "env", "HOME=/home/atlas-containers", "XDG_RUNTIME_DIR=/run/user/2000", "/usr/bin/systemctl", "--user"}
	return m.runner.Run(ctx, "", "/usr/sbin/runuser", append(prefix, args...)...)
}

func (m *Manager) podman(ctx context.Context, args ...string) (string, error) {
	// The transient user unit runs outside atlas-management.service's read-only
	// home mount namespace, while still executing as the rootless Podman owner.
	prefix := []string{"-u", "atlas-containers", "--", "env", "HOME=/home/atlas-containers", "XDG_RUNTIME_DIR=/run/user/2000", "/usr/bin/systemd-run", "--user", "--wait", "--collect", "--quiet", "--unit=atlas-tailscale-state-remove", "/usr/bin/podman"}
	return m.runner.Run(ctx, "", "/usr/sbin/runuser", append(prefix, args...)...)
}

func validateToken(name, value string) error {
	if value == "" || len(value) > 8192 || strings.TrimSpace(value) != value {
		return fmt.Errorf("%s must contain between 1 and 8192 non-whitespace characters", name)
	}
	for _, character := range value {
		if (character >= 'a' && character <= 'z') || (character >= 'A' && character <= 'Z') || (character >= '0' && character <= '9') || strings.ContainsRune("-_.=+/", character) {
			continue
		}
		return fmt.Errorf("%s contains an invalid character", name)
	}
	return nil
}

func validateHostname(value string) error {
	if value == "" {
		return nil
	}
	if len(value) > 253 {
		return fmt.Errorf("hostname must not exceed 253 characters")
	}
	for _, label := range strings.Split(value, ".") {
		if label == "" || len(label) > 63 || label[0] == '-' || label[len(label)-1] == '-' {
			return fmt.Errorf("hostname must be a valid DNS name")
		}
		for _, character := range label {
			if (character >= 'a' && character <= 'z') || (character >= 'A' && character <= 'Z') || (character >= '0' && character <= '9') || character == '-' {
				continue
			}
			return fmt.Errorf("hostname must be a valid DNS name")
		}
	}
	return nil
}
