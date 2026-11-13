package api_test

import (
	"testing"
	"time"

	"sh.simple/simpled/internal/agents"
	"sh.simple/simpled/internal/api"
	"sh.simple/simpled/internal/config"
	"sh.simple/simpled/internal/inbox"
	"sh.simple/simpled/internal/wire"
)

func TestPromptResolveWhenScreenClears(t *testing.T) {
	store := config.NewStore(t.TempDir(), t.TempDir())
	_ = store.EnsureDirs()
	agentStore := agents.NewStore(func(string, any) {})
	var resolved int
	inboxStore := inbox.New(store, func(ev string, _ any) {
		if ev == "inbox.update" {
			resolved++
		}
	})
	tracker := api.NewPromptTracker(agentStore, inboxStore)
	agentStore.Upsert(wire.Agent{ID: "t1", Kind: "shell", Title: "bash", Status: "unknown"})

	waiting := "Overwrite config.json? [y/N] "
	tracker.OnScreen("t1", waiting)
	time.Sleep(650 * time.Millisecond)
	tracker.Tick()
	tracker.Tick()
	items, _ := inboxStore.List()
	if len(items) != 1 || items[0].Resolved {
		t.Fatalf("open item expected, got %v", items)
	}

	tracker.OnScreen("t1", "you said y\nsimple@host:~/demo$ ")
	time.Sleep(650 * time.Millisecond)
	tracker.Tick()
	tracker.Tick()
	items, _ = inboxStore.List()
	if len(items) != 1 || !items[0].Resolved {
		t.Fatalf("expected resolved item, got %+v", items)
	}
	if resolved == 0 {
		t.Fatal("expected inbox.update on resolve")
	}
}
