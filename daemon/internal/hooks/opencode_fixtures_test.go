package hooks_test

import (
	"encoding/json"
	"os"
	"path/filepath"
	"runtime"
	"testing"
)

func TestOpenCodePermissionFixtures(t *testing.T) {
	_, file, _, ok := runtime.Caller(0)
	if !ok {
		t.Fatal("caller")
	}
	dir := filepath.Join(filepath.Dir(file), "..", "..", "..", "proto", "fixtures")
	hook, err := os.ReadFile(filepath.Join(dir, "opencode-permission-hook-result.json"))
	if err != nil {
		t.Fatal(err)
	}
	var hookOut struct {
		Status string `json:"status"`
	}
	if json.Unmarshal(hook, &hookOut) != nil || hookOut.Status != "allow" {
		t.Fatalf("hook %#v", hookOut)
	}
	ev, err := os.ReadFile(filepath.Join(dir, "opencode-permission-event-result.json"))
	if err != nil {
		t.Fatal(err)
	}
	var evOut struct {
		Reply string `json:"reply"`
	}
	if json.Unmarshal(ev, &evOut) != nil || evOut.Reply != "once" {
		t.Fatalf("event %#v", evOut)
	}
}
