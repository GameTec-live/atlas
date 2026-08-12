package containers

import (
	"context"
	"log/slog"
	"net/http"
)

type runtime interface {
	Ping(context.Context) error
	Restart(context.Context) error
}

type API struct {
	runtime runtime
	running chan struct{}
}

// NewAPI exposes only runtime readiness and the fixed consumer reload operation.
func NewAPI(runtime runtime) http.Handler {
	api := &API{runtime: runtime, running: make(chan struct{}, 1)}
	router := http.NewServeMux()
	router.HandleFunc("GET /healthz", api.health)
	router.HandleFunc("POST /restart", api.restart)
	return router
}

func (api *API) health(response http.ResponseWriter, request *http.Request) {
	if err := api.runtime.Ping(request.Context()); err != nil {
		slog.Error("check container runtime", "error", err)
		http.Error(response, "container runtime unavailable", http.StatusServiceUnavailable)
		return
	}
	response.WriteHeader(http.StatusNoContent)
}

func (api *API) restart(response http.ResponseWriter, request *http.Request) {
	select {
	case api.running <- struct{}{}:
		defer func() { <-api.running }()
	default:
		http.Error(response, "restart already in progress", http.StatusConflict)
		return
	}

	if err := api.runtime.Restart(request.Context()); err != nil {
		slog.Error("restart geodata consumers", "error", err)
		http.Error(response, "restart failed", http.StatusInternalServerError)
		return
	}
	response.WriteHeader(http.StatusNoContent)
}
