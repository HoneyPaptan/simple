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

func TestPromptTrackerIgnoresSinglePollEcho(t *testing.T) {
	store := config.NewStore(t.TempDir(), t.TempDir())
	_ = store.EnsureDirs()
	agentStore := agents.NewStore(func(string, any) {})
	inboxStore := inbox.New(store, func(string, any) {})
	agentStore.Upsert(wire.Agent{ID: "t1", Kind: "shell", Title: "bash", Status: "unknown"})
	tracker := api.NewPromptTracker(agentStore, inboxStore)

	echo := "simple@box:~/demo$ clear; select f in apple banana cherry\n1) apple\n2) banana"
	tracker.OnScreen("t1", echo)
	time.Sleep(650 * time.Millisecond)
	tracker.Tick()
	items, _ := inboxStore.List()
	if len(items) != 0 {
		t.Fatalf("single poll should not add item, got %+v", items)
	}

	stable := "Pick one\n#?\n1) apple\n2) banana\n3) cherry"
	tracker.OnScreen("t1", stable)
	time.Sleep(650 * time.Millisecond)
	tracker.Tick()
	tracker.Tick()
	items, _ = inboxStore.List()
	if len(items) != 1 || items[0].Kind != "prompt" {
		t.Fatalf("stable screen expected one prompt, got %+v", items)
	}
}
