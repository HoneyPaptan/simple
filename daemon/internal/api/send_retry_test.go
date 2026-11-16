package api

import (
	"context"
	"sync"
	"testing"
	"time"

	"sh.simple/simpled/internal/agents"
	"sh.simple/simpled/internal/hub"
	"sh.simple/simpled/internal/wire"
)

func TestFailedSendFreesCidSoRetryReachesTheTerminal(t *testing.T) {
	var mu sync.Mutex
	var sends []map[string]any
	hcClient := startFakeHCKill(t, &mu, "terminal.send", &sends)
	agentStore := agents.NewStore(func(string, any) {})
	agentStore.Upsert(wire.Agent{ID: "t1", Kind: "shell", Status: "idle"})
	srv := New(Options{HC: hcClient, Agents: agentStore, Hub: hub.New("e_retry")})
	t.Cleanup(func() { cancelSendWatch(srv, "t1") })
	sess := &session{srv: srv, helloOK: true, deviceID: "phone", captureWS: func([]byte) {}}
	p := []byte(`{"id":"t1","text":"hello","cid":"c1"}`)
	ctx := context.Background()
	count := func() int {
		mu.Lock()
		defer mu.Unlock()
		return len(sends)
	}
	sess.dispatch(ctx, 1, "agent.send", p)
	if count() != 1 {
		t.Fatalf("first send %d", count())
	}
	cancelSendWatch(srv, "t1")
	sess.dispatch(ctx, 2, "agent.send", p)
	if count() != 1 {
		t.Fatalf("claimed cid was sent again: %d", count())
	}
	srv.latchFailedSend("t1", "Not logged in")
	q := srv.promptQueue()
	q.mu.Lock()
	q.lastSent["t1"] = time.Now().Add(-time.Minute)
	q.mu.Unlock()
	sess.dispatch(ctx, 3, "agent.send", p)
	if count() != 2 {
		t.Fatalf("retry after failure %d", count())
	}
}

func cancelSendWatch(srv *Server, id string) {
	srv.sendWatchMu.Lock()
	defer srv.sendWatchMu.Unlock()
	if cancel, ok := srv.sendWatch[id]; ok {
		cancel()
		delete(srv.sendWatch, id)
	}
}
