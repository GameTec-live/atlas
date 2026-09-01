package reset

import (
	"context"
	"os"
	"path/filepath"
	"testing"
)

type fakeRunner struct {
	calls int
}

func (r *fakeRunner) Run(context.Context, string, string, ...string) (string, error) {
	r.calls++
	return "", nil
}

func TestFactoryResetDeletesDataAndPreservesImages(t *testing.T) {
	root := t.TempDir()
	persistent := filepath.Join(root, "persistent")
	home := filepath.Join(root, "home")
	containerHome := filepath.Join(home, "atlas-containers")
	stateDir := filepath.Join(persistent, "atlas", "system")
	paths := []string{
		filepath.Join(stateDir, markerName),
		filepath.Join(persistent, "shared", "network", "profile"),
		filepath.Join(persistent, "common", "etc", "machine-id"),
		filepath.Join(persistent, "slots", "system_a", "var", "lib", "NetworkManager", "secret_key"),
		filepath.Join(persistent, "slots", "system_b", "var", "log", "old.log"),
		filepath.Join(home, "atlas", "user-data"),
		filepath.Join(containerHome, ".config", "atlas", "api.env"),
		filepath.Join(containerHome, ".local", "share", "containers", "storage", "overlay-images", "image"),
		filepath.Join(containerHome, ".local", "share", "unrelated", "data"),
	}
	for _, path := range paths {
		if err := os.MkdirAll(filepath.Dir(path), 0o700); err != nil {
			t.Fatal(err)
		}
		if err := os.WriteFile(path, []byte("data"), 0o600); err != nil {
			t.Fatal(err)
		}
	}
	runner := &fakeRunner{}
	executor := Executor{StateDir: stateDir, PersistentRoot: persistent, HomeRoot: home, ContainerHome: containerHome, AdminUID: -1, ContainerUID: -1, Runner: runner, Sync: func() {}}
	if err := executor.Execute(context.Background()); err != nil {
		t.Fatal(err)
	}
	if runner.calls != 3 {
		t.Fatalf("expected three Podman cleanup calls, got %d", runner.calls)
	}
	if _, err := os.Stat(filepath.Join(containerHome, ".local", "share", "containers", "storage", "overlay-images", "image")); err != nil {
		t.Fatalf("image store was not preserved: %v", err)
	}
	for _, path := range []string{
		filepath.Join(stateDir, markerName),
		filepath.Join(persistent, "shared", "network", "profile"),
		filepath.Join(persistent, "common", "etc", "machine-id"),
		filepath.Join(persistent, "slots", "system_a", "var", "lib", "NetworkManager", "secret_key"),
		filepath.Join(persistent, "slots", "system_b", "var", "log", "old.log"),
		filepath.Join(containerHome, ".config", "atlas", "api.env"),
		filepath.Join(containerHome, ".local", "share", "unrelated"),
	} {
		if _, err := os.Stat(path); !os.IsNotExist(err) {
			t.Fatalf("reset data still exists at %s", path)
		}
	}
	if entries, err := os.ReadDir(filepath.Join(home, "atlas")); err != nil || len(entries) != 0 {
		t.Fatalf("administrator home was not recreated empty: entries=%#v error=%v", entries, err)
	}
	if entries, err := os.ReadDir(filepath.Join(containerHome, ".config", "atlas")); err != nil || len(entries) != 0 {
		t.Fatalf("container configuration was not recreated empty: entries=%#v error=%v", entries, err)
	}
}
