package reloader

import (
	"context"
	"fmt"
	"net/http"
	"strings"
	"time"
)

type Client struct {
	endpoint string
	http     *http.Client
}

func NewClient(baseURL string, timeout time.Duration) *Client {
	return &Client{
		endpoint: strings.TrimRight(baseURL, "/") + "/restart",
		http:     &http.Client{Timeout: timeout},
	}
}

func (c *Client) Restart(ctx context.Context) error {
	request, err := http.NewRequestWithContext(ctx, http.MethodPost, c.endpoint, nil)
	if err != nil {
		return err
	}
	response, err := c.http.Do(request)
	if err != nil {
		return fmt.Errorf("request consumer reload: %w", err)
	}
	defer response.Body.Close()
	if response.StatusCode != http.StatusNoContent {
		return fmt.Errorf("request consumer reload: reloader returned HTTP %d", response.StatusCode)
	}
	return nil
}
