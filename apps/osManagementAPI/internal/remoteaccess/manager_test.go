package remoteaccess

import (
	"context"
	"os"
	"path/filepath"
	"runtime"
	"slices"
	"strings"
	"testing"
)

type call struct {
	name string
	args []string
}

type fakeRunner struct {
	calls []call
}

func (r *fakeRunner) Run(_ context.Context, _ string, name string, args ...string) (string, error) {
	r.calls = append(r.calls, call{name: name, args: slices.Clone(args)})
	if slices.Contains(args, "show") {
		return "LoadState=loaded\nActiveState=active\nSubState=running", nil
	}
	return "", nil
}

func TestProvisionAndRemoveProviders(t *testing.T) {
	directory := t.TempDir()
	runner := &fakeRunner{}
	manager := New(directory, -1, runner)

	if err := manager.ProvisionCloudflare(context.Background(), CloudflareRequest{Token: "cloudflare-token_123="}); err != nil {
		t.Fatal(err)
	}
	cloudflare, err := os.ReadFile(filepath.Join(directory, cloudflareConfig))
	if err != nil || string(cloudflare) != "TUNNEL_TOKEN=cloudflare-token_123=\n" {
		t.Fatalf("unexpected Cloudflare config: %q, %v", cloudflare, err)
	}
	if info, err := os.Stat(filepath.Join(directory, cloudflareConfig)); err != nil || (runtime.GOOS != "windows" && info.Mode().Perm() != 0o600) {
		t.Fatalf("Cloudflare config has unsafe permissions: %#v, %v", info, err)
	}

	if err := manager.ProvisionTailscale(context.Background(), TailscaleRequest{AuthKey: "tskey-auth-test", Hostname: "atlas-1"}); err != nil {
		t.Fatal(err)
	}
	tailscale, err := os.ReadFile(filepath.Join(directory, tailscaleConfig))
	if err != nil || string(tailscale) != "TS_AUTHKEY=tskey-auth-test\nTS_HOSTNAME=atlas-1\n" {
		t.Fatalf("unexpected Tailscale config: %q, %v", tailscale, err)
	}

	status, err := manager.Status(context.Background())
	if err != nil {
		t.Fatal(err)
	}
	if !status.CloudflareTunnel.Provisioned || status.CloudflareTunnel.State != "active" || !status.Tailscale.Provisioned || status.Tailscale.Detail != "running" {
		t.Fatalf("unexpected status: %#v", status)
	}

	if err := manager.RemoveCloudflare(context.Background()); err != nil {
		t.Fatal(err)
	}
	status, err = manager.Status(context.Background())
	if err != nil {
		t.Fatal(err)
	}
	if status.CloudflareTunnel.Provisioned || status.CloudflareTunnel.State != "not_provisioned" {
		t.Fatalf("unexpected removed status: %#v", status.CloudflareTunnel)
	}

	joined := make([]string, 0, len(runner.calls))
	for _, call := range runner.calls {
		joined = append(joined, strings.Join(call.args, " "))
	}
	if !slices.ContainsFunc(joined, func(value string) bool { return strings.HasSuffix(value, "--user restart "+cloudflareUnit) }) ||
		!slices.ContainsFunc(joined, func(value string) bool { return strings.HasSuffix(value, "--user restart "+tailscaleUnit) }) ||
		!slices.ContainsFunc(joined, func(value string) bool { return strings.HasSuffix(value, "--user stop "+cloudflareUnit) }) {
		t.Fatalf("missing lifecycle call: %#v", joined)
	}
	if err := manager.RemoveTailscale(context.Background()); err != nil {
		t.Fatal(err)
	}
	if !slices.ContainsFunc(runner.calls, func(call call) bool {
		arguments := strings.Join(call.args, " ")
		return call.name == "/usr/sbin/runuser" && strings.Contains(arguments, "/usr/bin/systemd-run --user --wait --collect") && strings.HasSuffix(arguments, "/usr/bin/podman volume rm --force --ignore atlas-tailscale-state")
	}) {
		t.Fatalf("Tailscale state was not removed: %#v", runner.calls)
	}
	if !slices.ContainsFunc(runner.calls, func(call call) bool {
		return strings.HasSuffix(strings.Join(call.args, " "), "--user stop "+tailscaleVolume)
	}) {
		t.Fatalf("Tailscale volume unit was not stopped: %#v", runner.calls)
	}
}

func TestRejectsUnsafeProviderConfiguration(t *testing.T) {
	manager := New(t.TempDir(), -1, &fakeRunner{})
	for name, operation := range map[string]func() error{
		"cloudflare newline": func() error {
			return manager.ProvisionCloudflare(context.Background(), CloudflareRequest{Token: "token\nINJECTED=value"})
		},
		"tailscale hostname": func() error {
			return manager.ProvisionTailscale(context.Background(), TailscaleRequest{AuthKey: "tskey-auth-test", Hostname: "-atlas"})
		},
	} {
		t.Run(name, func(t *testing.T) {
			if err := operation(); err == nil {
				t.Fatal("expected validation error")
			}
		})
	}
}

func TestStatusDoesNotStartUnprovisionedProviders(t *testing.T) {
	runner := &fakeRunner{}
	status, err := New(t.TempDir(), -1, runner).Status(context.Background())
	if err != nil {
		t.Fatal(err)
	}
	if status.CloudflareTunnel.State != "not_provisioned" || status.Tailscale.State != "not_provisioned" || len(runner.calls) != 0 {
		t.Fatalf("unexpected unprovisioned status: %#v, calls=%#v", status, runner.calls)
	}
}
