package containers

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"net"
	"net/http"
	"net/url"
	"os"
	"path"
	"sort"
	"strings"
	"time"
)

const consumerLabel = "live.gametec.atlas.geodata-consumer"

var requiredConsumers = []string{"router", "geocoder"}

type Client struct {
	socketPath string
	http       *http.Client
}

type containerSummary struct {
	ID     string            `json:"Id"`
	Names  []string          `json:"Names"`
	Labels map[string]string `json:"Labels"`
}

func NewClient(socketPath string, timeout time.Duration) *Client {
	transport := &http.Transport{
		DialContext: func(ctx context.Context, _, _ string) (net.Conn, error) {
			return (&net.Dialer{}).DialContext(ctx, "unix", socketPath)
		},
	}
	return &Client{
		socketPath: socketPath,
		http:       &http.Client{Transport: transport, Timeout: timeout},
	}
}

// Restart reloads the configured Compose services when a Docker-compatible
// socket is available. A missing socket intentionally disables this feature.
func (c *Client) Restart(ctx context.Context) error {
	if strings.TrimSpace(c.socketPath) == "" {
		return nil
	}
	if _, err := os.Stat(c.socketPath); errors.Is(err, os.ErrNotExist) {
		return nil
	} else if err != nil {
		return fmt.Errorf("inspect container socket: %w", err)
	}

	filters, err := json.Marshal(map[string][]string{
		"label": {consumerLabel},
	})
	if err != nil {
		return err
	}
	endpoint := "http://container-runtime/containers/json?all=true&filters=" + url.QueryEscape(string(filters))
	request, err := http.NewRequestWithContext(ctx, http.MethodGet, endpoint, nil)
	if err != nil {
		return err
	}
	response, err := c.http.Do(request)
	if err != nil {
		return fmt.Errorf("list containers: %w", err)
	}
	defer response.Body.Close()
	if response.StatusCode != http.StatusOK {
		return fmt.Errorf("list containers: runtime returned HTTP %d", response.StatusCode)
	}

	var found []containerSummary
	if err := json.NewDecoder(response.Body).Decode(&found); err != nil {
		return fmt.Errorf("decode container list: %w", err)
	}
	foundConsumers := make(map[string]struct{}, len(requiredConsumers))
	var targets []containerSummary
	for _, container := range found {
		consumer := container.Labels[consumerLabel]
		if consumer == "" {
			continue
		}
		foundConsumers[consumer] = struct{}{}
		targets = append(targets, container)
	}

	var missing []string
	for _, consumer := range requiredConsumers {
		if _, ok := foundConsumers[consumer]; !ok {
			missing = append(missing, consumer)
		}
	}
	if len(missing) > 0 {
		sort.Strings(missing)
		return fmt.Errorf("geodata consumers not found: %s", strings.Join(missing, ", "))
	}
	for _, container := range targets {
		consumer := container.Labels[consumerLabel]
		if err := c.restart(ctx, container.ID); err != nil {
			return fmt.Errorf("restart %s container %s: %w", consumer, displayName(container), err)
		}
	}
	return nil
}

func (c *Client) restart(ctx context.Context, id string) error {
	request, err := http.NewRequestWithContext(ctx, http.MethodPost, "http://container-runtime/containers/"+url.PathEscape(id)+"/restart?t=30", nil)
	if err != nil {
		return err
	}
	response, err := c.http.Do(request)
	if err != nil {
		return err
	}
	defer response.Body.Close()
	if response.StatusCode != http.StatusNoContent {
		return fmt.Errorf("runtime returned HTTP %d", response.StatusCode)
	}
	return nil
}

func displayName(container containerSummary) string {
	if len(container.Names) > 0 {
		return strings.TrimPrefix(path.Base(container.Names[0]), "/")
	}
	return container.ID
}
