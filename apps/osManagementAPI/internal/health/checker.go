package health

import (
	"context"
	"crypto/tls"
	"encoding/json"
	"fmt"
	"net"
	"net/http"
	"net/url"
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
