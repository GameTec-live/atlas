package credentials

import (
	"path/filepath"
	"testing"
)

func TestEnsureTokenCreatesAndReusesSecret(t *testing.T) {
	path := filepath.Join(t.TempDir(), "atlas", "management-token")
	first, err := EnsureToken(path, -1, -1)
	if err != nil {
		t.Fatal(err)
	}
	second, err := EnsureToken(path, -1, -1)
	if err != nil {
		t.Fatal(err)
	}
	if len(first) != 64 || second != first {
		t.Fatalf("token was not a stable 256-bit hex secret: first=%q second=%q", first, second)
	}
}
