package reloader

import (
	"context"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"
)

func TestClientRequestsRestart(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(response http.ResponseWriter, request *http.Request) {
		if request.Method != http.MethodPost || request.URL.Path != "/restart" {
			t.Fatalf("request = %s %s", request.Method, request.URL.Path)
		}
		response.WriteHeader(http.StatusNoContent)
	}))
	defer server.Close()

	if err := NewClient(server.URL+"/", time.Second).Restart(context.Background()); err != nil {
		t.Fatal(err)
	}
}

func TestClientRejectsFailedRestart(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(response http.ResponseWriter, _ *http.Request) {
		http.Error(response, "failed", http.StatusInternalServerError)
	}))
	defer server.Close()

	if err := NewClient(server.URL, time.Second).Restart(context.Background()); err == nil {
		t.Fatal("restart unexpectedly succeeded")
	}
}
