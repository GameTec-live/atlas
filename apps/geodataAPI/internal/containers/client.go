package containers

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
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
		if consumer == "router" {
			if err := c.removeRouterTiles(ctx, container.ID); err != nil {
				return fmt.Errorf("remove stale tiles from router container %s: %w", displayName(container), err)
			}
		}
		if err := c.restart(ctx, container.ID); err != nil {
			return fmt.Errorf("restart %s container %s: %w", consumer, displayName(container), err)
		}
	}
	return nil
}

func (c *Client) removeRouterTiles(ctx context.Context, id string) error {
	createBody, err := json.Marshal(struct {
		AttachStdout bool     `json:"AttachStdout"`
		AttachStderr bool     `json:"AttachStderr"`
		Cmd          []string `json:"Cmd"`
	}{
		AttachStdout: true,
		AttachStderr: true,
		Cmd: []string{
			"rm", "-rf",
			"/custom_files/valhalla_tiles.tar",
			"/custom_files/valhalla_tiles",
		},
	})
	if err != nil {
		return err
	}
	request, err := http.NewRequestWithContext(ctx, http.MethodPost, "http://container-runtime/containers/"+url.PathEscape(id)+"/exec", bytes.NewReader(createBody))
	if err != nil {
		return err
	}
	request.Header.Set("Content-Type", "application/json")
	response, err := c.http.Do(request)
	if err != nil {
		return err
	}
	if response.StatusCode != http.StatusCreated {
		response.Body.Close()
		return fmt.Errorf("create cleanup command: runtime returned HTTP %d", response.StatusCode)
	}
	var created struct {
		ID string `json:"Id"`
	}
	decodeErr := json.NewDecoder(response.Body).Decode(&created)
	closeErr := response.Body.Close()
	if decodeErr != nil || closeErr != nil {
		if decodeErr != nil {
			decodeErr = fmt.Errorf("decode cleanup command: %w", decodeErr)
		}
		return errors.Join(decodeErr, closeErr)
	}
	if created.ID == "" {
		return errors.New("runtime returned an empty cleanup command ID")
	}

	startBody := bytes.NewBufferString(`{"Detach":false,"Tty":false}`)
	request, err = http.NewRequestWithContext(ctx, http.MethodPost, "http://container-runtime/exec/"+url.PathEscape(created.ID)+"/start", startBody)
	if err != nil {
		return err
	}
	request.Header.Set("Content-Type", "application/json")
	response, err = c.http.Do(request)
	if err != nil {
		return err
	}
	if response.StatusCode != http.StatusOK {
		response.Body.Close()
		return fmt.Errorf("start cleanup command: runtime returned HTTP %d", response.StatusCode)
	}
	_, copyErr := io.Copy(io.Discard, response.Body)
	closeErr = response.Body.Close()
	if copyErr != nil || closeErr != nil {
		return errors.Join(copyErr, closeErr)
	}

	request, err = http.NewRequestWithContext(ctx, http.MethodGet, "http://container-runtime/exec/"+url.PathEscape(created.ID)+"/json", nil)
	if err != nil {
		return err
	}
	response, err = c.http.Do(request)
	if err != nil {
		return err
	}
	defer response.Body.Close()
	if response.StatusCode != http.StatusOK {
		return fmt.Errorf("inspect cleanup command: runtime returned HTTP %d", response.StatusCode)
	}
	var result struct {
		Running  bool `json:"Running"`
		ExitCode int  `json:"ExitCode"`
	}
	if err := json.NewDecoder(response.Body).Decode(&result); err != nil {
		return fmt.Errorf("decode cleanup result: %w", err)
	}
	if result.Running {
		return errors.New("cleanup command is still running")
	}
	if result.ExitCode != 0 {
		return fmt.Errorf("cleanup command exited with status %d", result.ExitCode)
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
