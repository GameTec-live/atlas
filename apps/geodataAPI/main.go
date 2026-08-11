package main

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/GameTec-live/atlas/apps/geodataAPI/internal/api"
	"github.com/GameTec-live/atlas/apps/geodataAPI/internal/catalog"
	"github.com/GameTec-live/atlas/apps/geodataAPI/internal/config"
	"github.com/GameTec-live/atlas/apps/geodataAPI/internal/jobs"
	"github.com/GameTec-live/atlas/apps/geodataAPI/internal/store"
)

func main() {
	if len(os.Args) == 2 && os.Args[1] == "healthcheck" {
		if err := healthcheck(); err != nil {
			fmt.Fprintln(os.Stderr, err)
			os.Exit(1)
		}
		return
	}

	cfg, err := config.Load()
	if err != nil {
		slog.Error("invalid configuration", "error", err)
		os.Exit(1)
	}

	dataStore, err := store.Open(cfg.DataDir)
	if err != nil {
		slog.Error("open datastore", "error", err)
		os.Exit(1)
	}

	regionCatalog := catalog.NewGeofabrik(cfg.CatalogURL, cfg.CatalogTTL, nil)
	manager := jobs.NewManager(cfg, dataStore, regionCatalog, nil)
	manager.Start()

	server := &http.Server{
		Addr:              cfg.ListenAddr,
		Handler:           api.NewRouter(manager, regionCatalog, dataStore, cfg),
		ReadHeaderTimeout: 10 * time.Second,
		IdleTimeout:       60 * time.Second,
	}

	go func() {
		slog.Info("geodata API listening", "address", cfg.ListenAddr, "data", cfg.DataDir)
		if err := server.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			slog.Error("serve", "error", err)
			os.Exit(1)
		}
	}()

	stop, cancel := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer cancel()
	<-stop.Done()

	ctx, shutdownCancel := context.WithTimeout(context.Background(), 20*time.Second)
	defer shutdownCancel()
	manager.Stop()
	if err := server.Shutdown(ctx); err != nil {
		slog.Error("shutdown", "error", err)
	}
}

func healthcheck() error {
	endpoint := os.Getenv("GEODATA_HEALTHCHECK_URL")
	if endpoint == "" {
		endpoint = "http://127.0.0.1:8080/healthz"
	}
	client := &http.Client{Timeout: 4 * time.Second}
	response, err := client.Get(endpoint)
	if err != nil {
		return fmt.Errorf("healthcheck request: %w", err)
	}
	defer response.Body.Close()
	if response.StatusCode != http.StatusOK {
		return fmt.Errorf("healthcheck returned HTTP %d", response.StatusCode)
	}
	return nil
}
