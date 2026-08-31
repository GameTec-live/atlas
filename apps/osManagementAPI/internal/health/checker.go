package health

import (
	"context"
	"crypto/tls"
	"encoding/json"
	"fmt"
	"net"
	"net/http"
	"net/url"
	"sort"
	"strings"
	"time"
)

var containers = []string{
	"atlas-web",
	"atlas-api",
	"atlas-db",
	"atlas-map",
	"atlas-router",
	"atlas-geocoder",
	"atlas-geodata-api",
	"atlas-geodata-reloader",
}

type containerState struct {
	State struct {
		Status string `json:"Status"`
		Health *struct {
			Status string `json:"Status"`
		} `json:"Health"`
	} `json:"State"`
}

type Container struct {
	Name    string `json:"name"`
	Image   string `json:"image"`
	ImageID string `json:"imageId"`
	Version string `json:"version"`
}

type listedContainer struct {
	ID      string            `json:"Id"`
	Names   []string          `json:"Names"`
	Image   string            `json:"Image"`
	ImageID string            `json:"ImageID"`
	State   string            `json:"State"`
	Labels  map[string]string `json:"Labels"`
}

type Checker struct {
	podmanClient *http.Client
	healthClient *http.Client
	healthURL    string
}

func New(podmanSocket, healthURL string) *Checker {
	podmanTransport := &http.Transport{
		DialContext: func(ctx context.Context, _, _ string) (net.Conn, error) {
			return (&net.Dialer{}).DialContext(ctx, "unix", podmanSocket)
		},
	}
	healthTransport := http.DefaultTransport.(*http.Transport).Clone()
	healthTransport.TLSClientConfig = &tls.Config{
		MinVersion:         tls.VersionTLS12,
		InsecureSkipVerify: true, // The appliance may use its own local CA.
	}
	return &Checker{
		podmanClient: &http.Client{Transport: podmanTransport, Timeout: 10 * time.Second},
		healthClient: &http.Client{Transport: healthTransport, Timeout: 10 * time.Second},
		healthURL:    healthURL,
	}
}

// RunningContainers returns the exact image identity for every running
// container. OCI version labels are preferred for display, while ImageID stays
// available as the immutable identity and as a fallback for unlabeled images.
func (c *Checker) RunningContainers(ctx context.Context) ([]Container, error) {
	request, err := http.NewRequestWithContext(ctx, http.MethodGet, "http://podman/v1.41/containers/json?all=false", nil)
	if err != nil {
		return nil, err
	}
	response, err := c.podmanClient.Do(request)
	if err != nil {
		return nil, fmt.Errorf("list running containers: %w", err)
	}
	defer response.Body.Close()
	if response.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("list running containers returned HTTP %d", response.StatusCode)
	}

	var listed []listedContainer
	if err := json.NewDecoder(response.Body).Decode(&listed); err != nil {
		return nil, fmt.Errorf("decode running containers: %w", err)
	}
	result := make([]Container, 0, len(listed))
	for _, item := range listed {
		if item.State != "running" {
			continue
		}
		name := item.ID
		if len(item.Names) > 0 {
			name = strings.TrimPrefix(item.Names[0], "/")
		}
		version := item.Labels["org.opencontainers.image.version"]
		if version == "" {
			version = item.ImageID
		}
		result = append(result, Container{Name: name, Image: item.Image, ImageID: item.ImageID, Version: version})
	}
	sort.Slice(result, func(i, j int) bool { return result[i].Name < result[j].Name })
	return result, nil
}

func (c *Checker) Healthy(ctx context.Context) (bool, string) {
	for _, name := range containers {
		request, err := http.NewRequestWithContext(ctx, http.MethodGet, "http://podman/v1.41/containers/"+url.PathEscape(name)+"/json", nil)
		if err != nil {
			return false, err.Error()
		}
		response, err := c.podmanClient.Do(request)
		if err != nil {
			return false, fmt.Sprintf("inspect %s: %v", name, err)
		}
		var inspected containerState
		decodeErr := json.NewDecoder(response.Body).Decode(&inspected)
		response.Body.Close()
		if response.StatusCode != http.StatusOK {
			return false, fmt.Sprintf("inspect %s returned HTTP %d", name, response.StatusCode)
		}
		if decodeErr != nil {
			return false, fmt.Sprintf("inspect %s: %v", name, decodeErr)
		}
		if inspected.State.Status != "running" {
			return false, fmt.Sprintf("%s is %s", name, inspected.State.Status)
		}
		if inspected.State.Health == nil {
			if name != "atlas-api" {
				return false, fmt.Sprintf("%s has no health check", name)
			}
		} else if inspected.State.Health.Status != "healthy" {
			return false, fmt.Sprintf("%s health is %s", name, inspected.State.Health.Status)
		}
	}

	request, err := http.NewRequestWithContext(ctx, http.MethodGet, c.healthURL, nil)
	if err != nil {
		return false, err.Error()
	}
	response, err := c.healthClient.Do(request)
	if err != nil {
		return false, "Atlas API reachability check failed: " + err.Error()
	}
	response.Body.Close()
	if response.StatusCode < 200 || response.StatusCode >= 300 {
		return false, fmt.Sprintf("Atlas API reachability check returned HTTP %d", response.StatusCode)
	}
	return true, ""
}
