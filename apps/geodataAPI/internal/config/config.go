package config

import (
	"fmt"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"time"
)

const defaultCatalogURL = "https://download.geofabrik.de/index-v1.json"

type Config struct {
	ListenAddr      string
	DataDir         string
	CatalogURL      string
	CatalogTTL      time.Duration
	HTTPTimeout     time.Duration
	ContainerSocket string
	ReloadTimeout   time.Duration
	OsmiumBinary    string
	PackgenBinary   string
	PlanetilerJar   string
	JavaBinary      string
	PlanetilerArgs  []string
}

func Load() (Config, error) {
	cfg := Config{
		ListenAddr:      env("GEODATA_LISTEN", ":8080"),
		DataDir:         env("GEODATA_DIR", "data"),
		CatalogURL:      env("GEODATA_CATALOG_URL", defaultCatalogURL),
		ContainerSocket: env("GEODATA_CONTAINER_SOCKET", "/var/run/docker.sock"),
		OsmiumBinary:    env("GEODATA_OSMIUM", "osmium"),
		PackgenBinary:   env("GEODATA_PACKGEN", "packgen"),
		PlanetilerJar:   strings.TrimSpace(os.Getenv("GEODATA_PLANETILER_JAR")),
		JavaBinary:      env("GEODATA_JAVA", "java"),
		PlanetilerArgs:  strings.Fields(os.Getenv("GEODATA_PLANETILER_ARGS")),
	}

	var err error
	if cfg.CatalogTTL, err = durationEnv("GEODATA_CATALOG_TTL", 24*time.Hour); err != nil {
		return Config{}, err
	}
	if cfg.HTTPTimeout, err = durationEnv("GEODATA_HTTP_TIMEOUT", 24*time.Hour); err != nil {
		return Config{}, err
	}
	if cfg.ReloadTimeout, err = durationEnv("GEODATA_RELOAD_TIMEOUT", 30*time.Second); err != nil {
		return Config{}, err
	}

	abs, err := filepath.Abs(cfg.DataDir)
	if err != nil {
		return Config{}, fmt.Errorf("resolve data directory: %w", err)
	}
	cfg.DataDir = abs
	return cfg, nil
}

func env(key, fallback string) string {
	if value := strings.TrimSpace(os.Getenv(key)); value != "" {
		return value
	}
	return fallback
}

func durationEnv(key string, fallback time.Duration) (time.Duration, error) {
	value := strings.TrimSpace(os.Getenv(key))
	if value == "" {
		return fallback, nil
	}
	if seconds, err := strconv.Atoi(value); err == nil {
		return time.Duration(seconds) * time.Second, nil
	}
	duration, err := time.ParseDuration(value)
	if err != nil {
		return 0, fmt.Errorf("%s must be seconds or a Go duration: %w", key, err)
	}
	return duration, nil
}
