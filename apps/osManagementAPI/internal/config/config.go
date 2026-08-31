package config

import (
	"fmt"
	"os"
	"strconv"
	"time"
)

type Config struct {
	SocketPath         string
	SocketGID          int
	TokenPath          string
	StateDir           string
	TrustedOriginsPath string
	ContainerHome      string
	ContainerUID       int
	HealthWindow       time.Duration
	HealthPollInterval time.Duration
	HealthURL          string
	PodmanSocket       string
	MaxUpdateBytes     int64
	ShutdownDelay      time.Duration
}

func Load() (Config, error) {
	cfg := Config{
		SocketPath:         env("ATLAS_MANAGEMENT_SOCKET", "/run/atlas-management/api.sock"),
		TokenPath:          env("ATLAS_MANAGEMENT_TOKEN_FILE", "/home/atlas-containers/.config/atlas/management-token"),
		StateDir:           env("ATLAS_MANAGEMENT_STATE_DIR", "/persistent/atlas/system"),
		TrustedOriginsPath: env("ATLAS_TRUSTED_ORIGINS_FILE", "/home/atlas-containers/.config/atlas/trusted-origins"),
		ContainerHome:      env("ATLAS_CONTAINER_HOME", "/home/atlas-containers"),
		HealthURL:          env("ATLAS_MANAGEMENT_HEALTH_URL", "https://127.0.0.1/api/api/auth/get-session"),
		PodmanSocket:       env("ATLAS_MANAGEMENT_PODMAN_SOCKET", "/run/user/2000/podman/podman.sock"),
	}

	var err error
	if cfg.SocketGID, err = envInt("ATLAS_MANAGEMENT_SOCKET_GID", 2000); err != nil {
		return Config{}, err
	}
	if cfg.ContainerUID, err = envInt("ATLAS_CONTAINER_UID", 2000); err != nil {
		return Config{}, err
	}
	if cfg.HealthWindow, err = envDuration("ATLAS_MANAGEMENT_HEALTH_WINDOW", 5*time.Minute); err != nil {
		return Config{}, err
	}
	if cfg.HealthPollInterval, err = envDuration("ATLAS_MANAGEMENT_HEALTH_POLL_INTERVAL", 5*time.Second); err != nil {
		return Config{}, err
	}
	if cfg.ShutdownDelay, err = envDuration("ATLAS_MANAGEMENT_SHUTDOWN_DELAY", time.Second); err != nil {
		return Config{}, err
	}
	if cfg.MaxUpdateBytes, err = envInt64("ATLAS_MANAGEMENT_MAX_UPDATE_BYTES", 4<<30); err != nil {
		return Config{}, err
	}
	if cfg.HealthWindow < 5*time.Minute {
		return Config{}, fmt.Errorf("ATLAS_MANAGEMENT_HEALTH_WINDOW must be at least 5m")
	}
	if cfg.SocketGID < 0 || cfg.ContainerUID < 0 {
		return Config{}, fmt.Errorf("socket group and container UID must not be negative")
	}
	if cfg.HealthPollInterval <= 0 || cfg.MaxUpdateBytes <= 0 {
		return Config{}, fmt.Errorf("health poll interval and maximum update size must be positive")
	}
	return cfg, nil
}

func env(name, fallback string) string {
	if value := os.Getenv(name); value != "" {
		return value
	}
	return fallback
}

func envInt(name string, fallback int) (int, error) {
	value := os.Getenv(name)
	if value == "" {
		return fallback, nil
	}
	parsed, err := strconv.Atoi(value)
	if err != nil {
		return 0, fmt.Errorf("%s: %w", name, err)
	}
	return parsed, nil
}

func envInt64(name string, fallback int64) (int64, error) {
	value := os.Getenv(name)
	if value == "" {
		return fallback, nil
	}
	parsed, err := strconv.ParseInt(value, 10, 64)
	if err != nil {
		return 0, fmt.Errorf("%s: %w", name, err)
	}
	return parsed, nil
}

func envDuration(name string, fallback time.Duration) (time.Duration, error) {
	value := os.Getenv(name)
	if value == "" {
		return fallback, nil
	}
	parsed, err := time.ParseDuration(value)
	if err != nil {
		return 0, fmt.Errorf("%s: %w", name, err)
	}
	return parsed, nil
}
