package reset

import (
	"context"
	"errors"
	"fmt"
	"os"
	"path/filepath"

	"github.com/GameTec-live/atlas/apps/osManagementAPI/internal/command"
	"github.com/GameTec-live/atlas/apps/osManagementAPI/internal/ownership"
)

const markerName = "factory-reset-pending"

type Requester struct {
	stateDir string
}

func NewRequester(stateDir string) *Requester {
	return &Requester{stateDir: stateDir}
}

func (r *Requester) Request() error {
	if err := os.MkdirAll(r.stateDir, 0o700); err != nil {
		return fmt.Errorf("create reset state directory: %w", err)
	}
	marker := filepath.Join(r.stateDir, markerName)
	if err := os.WriteFile(marker, []byte("pending\n"), 0o600); err != nil {
		return fmt.Errorf("write factory-reset marker: %w", err)
	}
	file, err := os.Open(r.stateDir)
	if err != nil {
		return err
	}
	defer file.Close()
	return file.Sync()
}

type Executor struct {
	StateDir       string
	PersistentRoot string
	HomeRoot       string
	ContainerHome  string
	AdminUID       int
	ContainerUID   int
	Runner         command.Runner
	Sync           func()
}

func (e *Executor) Pending() bool {
	_, err := os.Stat(filepath.Join(e.StateDir, markerName))
	return err == nil
}

// Execute removes appliance data but retains the rootless OCI image store.
// The original preload archive is deleted after first boot, so those image
// layers are the software payload required for an offline reset to boot.
func (e *Executor) Execute(ctx context.Context) error {
	if !e.Pending() {
		return nil
	}
	if err := e.cleanPodman(ctx); err != nil {
		return err
	}

	for _, name := range []string{"atlas", "shared", "common", "log"} {
		if err := clearTree(filepath.Join(e.PersistentRoot, name)); err != nil {
			return err
		}
	}
	for _, slot := range []string{"system_a", "system_b"} {
		slotVar := filepath.Join(e.PersistentRoot, "slots", slot, "var")
		for _, path := range []string{filepath.Join(slotVar, "lib", "NetworkManager"), filepath.Join(slotVar, "log")} {
			if err := clearTree(path); err != nil {
				return err
			}
		}
	}
	if err := removeExactChild(e.HomeRoot, "atlas"); err != nil {
		return err
	}
	if err := preserveContainerImages(e.ContainerHome); err != nil {
		return err
	}
	adminHome := filepath.Join(e.HomeRoot, "atlas")
	if err := os.MkdirAll(adminHome, 0o700); err != nil {
		return fmt.Errorf("recreate administrator home: %w", err)
	}
	if err := ownership.Set(adminHome, e.AdminUID, e.AdminUID); err != nil {
		return fmt.Errorf("set administrator home ownership: %w", err)
	}
	if err := os.MkdirAll(e.ContainerHome, 0o700); err != nil {
		return fmt.Errorf("recreate container home: %w", err)
	}
	if err := ownership.Set(e.ContainerHome, e.ContainerUID, e.ContainerUID); err != nil {
		return fmt.Errorf("set container home ownership: %w", err)
	}
	for _, path := range []string{filepath.Join(e.ContainerHome, ".config"), filepath.Join(e.ContainerHome, ".config", "atlas")} {
		if err := os.MkdirAll(path, 0o700); err != nil {
			return fmt.Errorf("recreate container configuration: %w", err)
		}
		if err := ownership.Set(path, e.ContainerUID, e.ContainerUID); err != nil {
			return fmt.Errorf("set container configuration ownership: %w", err)
		}
	}
	if e.Sync != nil {
		e.Sync()
	} else {
		syncFilesystems()
	}
	return nil
}

func (e *Executor) cleanPodman(ctx context.Context) error {
	prefix := []string{"-u", "atlas-containers", "--", "env", "HOME=" + e.ContainerHome, "XDG_RUNTIME_DIR=/run/user/2000", "/usr/bin/podman"}
	for _, args := range [][]string{
		{"rm", "--all", "--force"},
		{"volume", "rm", "--all", "--force"},
		{"network", "prune", "--force"},
	} {
		if _, err := e.Runner.Run(ctx, "", "/usr/sbin/runuser", append(prefix, args...)...); err != nil {
			return fmt.Errorf("clean container state: %w", err)
		}
	}
	return nil
}

func removeExactChild(root, name string) error {
	if root == "" || name == "" || filepath.Clean(root) == string(filepath.Separator) {
		return fmt.Errorf("refusing unsafe reset path")
	}
	target := filepath.Join(root, name)
	relative, err := filepath.Rel(root, target)
	if err != nil || relative != name {
		return fmt.Errorf("reset path escaped its root")
	}
	if err := os.RemoveAll(target); err != nil {
		return fmt.Errorf("remove %s: %w", target, err)
	}
	return nil
}

// clearTree removes data while retaining directory skeletons which may be the
// source of active bind mounts in the A/B persistent layout.
func clearTree(root string) error {
	if root == "" || filepath.Clean(root) == string(filepath.Separator) {
		return fmt.Errorf("refusing unsafe reset path")
	}
	if _, err := os.Stat(root); errors.Is(err, os.ErrNotExist) {
		return nil
	} else if err != nil {
		return err
	}
	return filepath.WalkDir(root, func(path string, entry os.DirEntry, walkErr error) error {
		if walkErr != nil {
			return walkErr
		}
		if path == root || entry.IsDir() {
			return nil
		}
		if err := os.Remove(path); err != nil {
			return fmt.Errorf("remove %s: %w", path, err)
		}
		return nil
	})
}

func preserveContainerImages(home string) error {
	if home == "" || filepath.Clean(home) == string(filepath.Separator) {
		return fmt.Errorf("refusing unsafe container home")
	}
	entries, err := os.ReadDir(home)
	if errors.Is(err, os.ErrNotExist) {
		return nil
	}
	if err != nil {
		return err
	}
	for _, entry := range entries {
		if entry.Name() != ".local" {
			if err := removeExactChild(home, entry.Name()); err != nil {
				return err
			}
		}
	}
	local := filepath.Join(home, ".local")
	if err := preserveOnly(local, "share"); err != nil {
		return err
	}
	share := filepath.Join(local, "share")
	if err := preserveOnly(share, "containers"); err != nil {
		return err
	}
	containers := filepath.Join(share, "containers")
	return preserveOnly(containers, "storage")
}

func preserveOnly(root, keep string) error {
	entries, err := os.ReadDir(root)
	if errors.Is(err, os.ErrNotExist) {
		return nil
	}
	if err != nil {
		return err
	}
	for _, entry := range entries {
		if entry.Name() != keep {
			if err := removeExactChild(root, entry.Name()); err != nil {
				return err
			}
		}
	}
	return nil
}
