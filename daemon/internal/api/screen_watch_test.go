package api_test

import (
	"context"
	"sync"
	"testing"
	"time"

	"sh.simple/simpled/internal/agents"
	"sh.simple/simpled/internal/api"
	"sh.simple/simpled/internal/config"
	"sh.simple/simpled/internal/hc"
	"sh.simple/simpled/internal/inbox"
)

func TestTwoWatchersOneUnwatch(t *testing.T) {
	home := t.TempDir()
	store := config.NewStore(home, t.TempDir())
	_ = store.EnsureDirs()
	sock := startFakeHCReads(t, "Overwrite config.json? [y/N] ")
	hcClient := hc.NewClient(sock)
	agentStore := agents.NewStore(func(string, any) {})
	inboxStore := inbox.New(store, func(string, any) {})
	prompts := api.NewPromptTracker(agentStore, inboxStore)
	var mu sync.Mutex
	events := 0
	screens := api.NewScreenWatcher(hcClient, nil, func(ev string, _ any) {
		if ev == "screen" {
			mu.Lock()
			events++
			mu.Unlock()
		}
	}, prompts, agentStore)
	screens.TestIntervals(30*time.Millisecond, 15*time.Millisecond, 10*time.Millisecond)
	agentStore.SetPollSync(screens.SyncPollers)
	agentStore.LoadSnapshot([]hc.Terminal{{
		Address: "t1", Argv: []string{"bash"}, Cwd: home, State: "live",
	}})

	sess1 := api.NewTestSession()
	sess2 := api.NewTestSession()
	screens.Start("t1", sess1)
	screens.Start("t1", sess2)
	ctx := context.Background()
	time.Sleep(200 * time.Millisecond)
	screens.Stop("t1", sess2)
	time.Sleep(200 * time.Millisecond)
	mu.Lock()
	after := events
	mu.Unlock()
	if after == 0 {
		t.Fatal("expected screen events while watching")
	}

	for i := 0; i < 3; i++ {
		screens.PollAgentOnce(ctx, "t1")
		time.Sleep(700 * time.Millisecond)
	}
	items, _ := inboxStore.List()
	if len(items) != 1 {
		t.Fatalf("duplicate prompts %d", len(items))
	}
}

func TestWatchAgentOutsideBackgroundPoll(t *testing.T) {
	home := t.TempDir()
	sock := startFakeHCReads(t, "fake codex screen\n")
	hcClient := hc.NewClient(sock)
	agentStore := agents.NewStore(func(string, any) {})
	prompts := api.NewPromptTracker(agentStore, inbox.New(config.NewStore(home, t.TempDir()), func(string, any) {}))
	var mu sync.Mutex
	events := 0
	screens := api.NewScreenWatcher(hcClient, nil, func(ev string, _ any) {
		if ev == "screen" {
			mu.Lock()
			events++
			mu.Unlock()
		}
	}, prompts, agentStore)
	screens.TestIntervals(30*time.Millisecond, 15*time.Millisecond, 10*time.Millisecond)
	agentStore.SetPollSync(screens.SyncPollers)
	agentStore.LoadSnapshot([]hc.Terminal{{
		Address: "t1", Argv: []string{"codex"}, Cwd: home, State: "live",
	}})
	screens.SyncPollers()
	sess := api.NewTestSession()
	screens.Start("t1", sess)
	time.Sleep(250 * time.Millisecond)
	mu.Lock()
	got := events
	mu.Unlock()
	if got == 0 {
		t.Fatal("expected screen events for watched codex agent")
	}
}

func TestNewWatcherGetsUnchangedScreen(t *testing.T) {
	home := t.TempDir()
	sock := startFakeHCReads(t, "fake codex screen\n")
	hcClient := hc.NewClient(sock)
	agentStore := agents.NewStore(func(string, any) {})
	prompts := api.NewPromptTracker(agentStore, inbox.New(config.NewStore(home, t.TempDir()), func(string, any) {}))
	var mu sync.Mutex
	events := 0
	screens := api.NewScreenWatcher(hcClient, nil, func(ev string, _ any) {
		if ev == "screen" {
			mu.Lock()
			events++
			mu.Unlock()
		}
	}, prompts, agentStore)
	screens.TestIntervals(30*time.Millisecond, 15*time.Millisecond, 10*time.Millisecond)
	agentStore.SetPollSync(screens.SyncPollers)
	agentStore.LoadSnapshot([]hc.Terminal{{
		Address: "t1", Argv: []string{"codex"}, Cwd: home, State: "live",
	}})
	screens.SyncPollers()
	count := func() int {
		mu.Lock()
		defer mu.Unlock()
		return events
	}
	first := api.NewTestSession()
	screens.Start("t1", first)
	time.Sleep(150 * time.Millisecond)
	screens.StopAll(first)
	time.Sleep(150 * time.Millisecond)
	before := count()
	screens.Start("t1", api.NewTestSession())
	time.Sleep(150 * time.Millisecond)
	if count() == before {
		t.Fatal("a watcher that joins after the screen settled got no screen")
	}
}
