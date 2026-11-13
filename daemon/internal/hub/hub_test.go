package hub_test

import (
	"testing"

	"sh.simple/simpled/internal/hub"
	"sh.simple/simpled/internal/wire"
)

func TestRingSince(t *testing.T) {
	h := hub.New("e_test")
	for i := 0; i < 10; i++ {
		h.Publish("agent.update", wire.Agent{ID: "t1", Kind: "shell", Status: "idle"})
	}
	evs, ok := h.Since(5)
	if !ok || len(evs) != 5 {
		t.Fatalf("since: ok=%v len=%d", ok, len(evs))
	}
	if evs[0].Seq != 6 {
		t.Fatalf("seq %d", evs[0].Seq)
	}
}

func TestSinceTooOld(t *testing.T) {
	h := hub.New("e_test")
	for i := 0; i < 5000; i++ {
		h.Publish("agent.update", wire.Agent{ID: "t1"})
	}
	_, ok := h.Since(1)
	if ok {
		t.Fatal("expected false for old since")
	}
}

func TestSlowSubscriberDropped(t *testing.T) {
	h := hub.New("e_test")
	ch, unsub := h.Subscribe()
	defer unsub()
	for i := 0; i < 200; i++ {
		h.Publish("agent.update", wire.Agent{ID: "t1"})
	}
	select {
	case <-ch:
	default:
	}
}
