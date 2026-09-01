package main

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"net"
	"net/http"
	"os"
	"os/signal"
	"path/filepath"
	"syscall"
	"time"

	"github.com/GameTec-live/atlas/apps/osManagementAPI/internal/api"
	"github.com/GameTec-live/atlas/apps/osManagementAPI/internal/command"
	"github.com/GameTec-live/atlas/apps/osManagementAPI/internal/config"
	"github.com/GameTec-live/atlas/apps/osManagementAPI/internal/credentials"
	"github.com/GameTec-live/atlas/apps/osManagementAPI/internal/health"
	"github.com/GameTec-live/atlas/apps/osManagementAPI/internal/networkmanager"
	"github.com/GameTec-live/atlas/apps/osManagementAPI/internal/origins"
	"github.com/GameTec-live/atlas/apps/osManagementAPI/internal/power"
	"github.com/GameTec-live/atlas/apps/osManagementAPI/internal/remoteaccess"
	"github.com/GameTec-live/atlas/apps/osManagementAPI/internal/reset"
	sshmanager "github.com/GameTec-live/atlas/apps/osManagementAPI/internal/ssh"
	"github.com/GameTec-live/atlas/apps/osManagementAPI/internal/update"
)

type realScheduler struct{}

func (realScheduler) After(delay time.Duration, operation func()) {
	time.AfterFunc(delay, operation)
}

func main() {
	cfg, err := config.Load()
	if err != nil {
		slog.Error("invalid configuration", "error", err)
		os.Exit(1)
	}
	runner := command.ExecRunner{}

	if len(os.Args) == 2 && os.Args[1] == "factory-reset" {
		executor := reset.Executor{
			StateDir:       cfg.StateDir,
			PersistentRoot: "/persistent",
			HomeRoot:       "/home",
			ContainerHome:  cfg.ContainerHome,
			AdminUID:       1000,
			ContainerUID:   cfg.ContainerUID,
			Runner:         runner,
		}
		if err := executor.Execute(context.Background()); err != nil {
			slog.Error("execute factory reset", "error", err)
			os.Exit(1)
		}
		return
	}
	if len(os.Args) != 1 {
		fmt.Fprintln(os.Stderr, "usage: atlas-management [factory-reset]")
		os.Exit(2)
	}

	token, err := credentials.EnsureToken(cfg.TokenPath, cfg.ContainerUID, cfg.SocketGID)
	if err != nil {
		slog.Error("initialize API token", "error", err)
		os.Exit(1)
	}
	listener, err := listenUnix(cfg.SocketPath, cfg.SocketGID)
	if err != nil {
		slog.Error("listen on management socket", "error", err)
		os.Exit(1)
	}
	defer func() {
		listener.Close()
		os.Remove(cfg.SocketPath)
	}()

	updateManager := update.New(runner)
	healthChecker := health.New(cfg.PodmanSocket, cfg.HealthURL)
	trialMonitor := update.NewMonitor(updateManager, healthChecker, cfg.HealthWindow, cfg.HealthPollInterval)
	powerManager := power.New(runner)
	router := api.NewRouter(api.Dependencies{
		Token:          token,
		StateDir:       cfg.StateDir,
		MaxUpdateBytes: cfg.MaxUpdateBytes,
		ShutdownDelay:  cfg.ShutdownDelay,
		Update:         updateManager,
		Monitor:        trialMonitor,
		Containers:     healthChecker,
		Power:          powerManager,
		Reset:          reset.NewRequester(cfg.StateDir),
		SSH:            sshmanager.New(runner),
		Network:        networkmanager.New(runner),
		Origins:        origins.New(cfg.TrustedOriginsPath, cfg.ContainerUID, runner),
		RemoteAccess:   remoteaccess.New(cfg.RemoteAccessDir, cfg.ContainerUID, runner),
		Scheduler:      realScheduler{},
	})
	server := &http.Server{
		Handler:           router,
		ReadHeaderTimeout: 10 * time.Second,
		IdleTimeout:       60 * time.Second,
	}

	stop, cancel := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer cancel()
	go trialMonitor.Run(stop)
	go func() {
		slog.Info("Atlas OS management API listening", "socket", cfg.SocketPath)
		if err := server.Serve(listener); err != nil && !errors.Is(err, http.ErrServerClosed) {
			slog.Error("serve management API", "error", err)
			cancel()
		}
	}()
	<-stop.Done()

	shutdownContext, shutdownCancel := context.WithTimeout(context.Background(), 20*time.Second)
	defer shutdownCancel()
	if err := server.Shutdown(shutdownContext); err != nil {
		slog.Error("shut down management API", "error", err)
	}
}

func listenUnix(path string, gid int) (net.Listener, error) {
	if err := os.MkdirAll(filepath.Dir(path), 0o750); err != nil {
		return nil, err
	}
	if info, err := os.Lstat(path); err == nil {
		if info.Mode()&os.ModeSocket == 0 {
			return nil, fmt.Errorf("refusing to replace non-socket path %s", path)
		}
		if err := os.Remove(path); err != nil {
			return nil, err
		}
	} else if !errors.Is(err, os.ErrNotExist) {
		return nil, err
	}
	listener, err := net.Listen("unix", path)
	if err != nil {
		return nil, err
	}
	if err := os.Chmod(path, 0o660); err != nil {
		listener.Close()
		return nil, err
	}
	if err := os.Chown(path, 0, gid); err != nil {
		listener.Close()
		return nil, err
	}
	return listener, nil
}
