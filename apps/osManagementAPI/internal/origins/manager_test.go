package origins

import (
	"context"
	"path/filepath"
	"testing"
)

type fakeRunner struct {
	calls int
}

func (r *fakeRunner) Run(context.Context, string, string, ...string) (string, error) {
	r.calls++
	return "", nil
}

func TestAddAndRemoveOriginAtomically(t *testing.T) {
	path := filepath.Join(t.TempDir(), "atlas", "trusted-origins")
	runner := &fakeRunner{}
	manager := New(path, -1, runner)
	items, err := manager.Add(context.Background(), "https://atlas.example.com:8443/")
	if err != nil {
		t.Fatal(err)
	}
	if len(items) != 1 || items[0] != "https://atlas.example.com:8443" || runner.calls != 1 {
		t.Fatalf("unexpected add result: items=%#v calls=%d", items, runner.calls)
	}
	items, err = manager.Remove(context.Background(), "https://atlas.example.com:8443")
	if err != nil {
		t.Fatal(err)
	}
	if len(items) != 0 || runner.calls != 2 {
		t.Fatalf("unexpected remove result: items=%#v calls=%d", items, runner.calls)
	}
}

func TestRejectsOriginWithPath(t *testing.T) {
	manager := New(filepath.Join(t.TempDir(), "origins"), -1, &fakeRunner{})
	if _, err := manager.Add(context.Background(), "https://atlas.example.com/admin"); err == nil {
		t.Fatal("expected origin path to be rejected")
	}
}
