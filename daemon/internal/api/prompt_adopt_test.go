package api_test

import (
	"testing"
	"time"

	"sh.simple/simpled/internal/agents"
	"sh.simple/simpled/internal/api"
	"sh.simple/simpled/internal/config"
	"sh.simple/simpled/internal/detect"
	"sh.simple/simpled/internal/inbox"
	"sh.simple/simpled/internal/wire"
)

func TestPromptTrackerAdoptsNewestOpenAfterRestart(t *testing.T) {
	store := config.NewStore(t.TempDir(), t.TempDir())
	_ = store.EnsureDirs()
	agentStore := agents.NewStore(func(string, any) {})
	inboxStore := inbox.New(store, func(string, any) {})
	agentStore.Upsert(wire.Agent{ID: "t1", Kind: "shell", Title: "bash", Status: "unknown"})

	screen := "Overwrite config.json? [y/N] "
	d := "yesno"
	_ = inboxStore.Add(wire.Item{
		ID: "i_old", Agent: "t1", Kind: "prompt", Title: "Old", Detector: &d, CreatedMs: 1,
	})
	_ = inboxStore.Add(wire.Item{
		ID:        "i_new",
		Agent:     "t1",
		Kind:      "prompt",
		Title:     "Overwrite config.json?",
		Detector:  &d,
		CreatedMs: 2,
	})

	tracker := api.NewPromptTracker(agentStore, inboxStore)
	tracker.OnScreen("t1", screen)
	time.Sleep(650 * time.Millisecond)
	tracker.Tick()
	tracker.Tick()

	items, _ := inboxStore.List()
	var open []wire.Item
	for _, it := range items {
		if it.Kind == "prompt" && !it.Resolved {
			open = append(open, it)
		}
	}
	if len(open) != 1 || open[0].ID != "i_new" {
		t.Fatalf("open prompts: %+v", open)
	}
	fp := detect.FingerprintForItem(open[0])
	if fp == "" {
		t.Fatal("fingerprint")
	}
	tracker.OnScreen("t1", screen)
	time.Sleep(650 * time.Millisecond)
	tracker.Tick()
	tracker.Tick()
	items, _ = inboxStore.List()
	open = open[:0]
	for _, it := range items {
		if it.Kind == "prompt" && !it.Resolved {
			open = append(open, it)
		}
	}
	if len(open) != 1 {
		t.Fatalf("duplicate after stable screen: %+v", open)
	}
}
