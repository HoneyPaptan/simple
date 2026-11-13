package api

import (
	"context"
	"strings"
	"testing"
	"time"

	"sh.simple/simpled/internal/hub"
)

func TestFanoutSurvivesSubscriberOverflow(t *testing.T) {
	h := hub.New("e_test")
	s := New(Options{Hub: h})
	sess := &session{srv: s, send: make(chan []byte, 100000)}
	s.sessions[sess] = struct{}{}
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	s.mu.Lock()
	go s.fanout(ctx)
	time.Sleep(20 * time.Millisecond)
	for i := 0; i < 500; i++ {
		h.Publish("agent.update", map[string]int{"i": i})
	}
	s.mu.Unlock()
	time.Sleep(50 * time.Millisecond)
	h.Publish("inbox.add", map[string]string{"id": "after"})
	deadline := time.After(2 * time.Second)
	for {
		select {
		case frame := <-sess.send:
			if strings.Contains(string(frame), `"inbox.add"`) {
				return
			}
		case <-deadline:
			t.Fatal("events stopped flowing after the fanout subscriber overflowed")
		}
	}
}


func TestOverflowingSessionLeavesFanoutOnce(t *testing.T) {
	h := hub.New("e_test")
	s := New(Options{Hub: h})
	stuck := &session{srv: s, send: make(chan []byte), closed: true}
	s.sessions[stuck] = struct{}{}
	for i := 0; i < 100; i++ {
		s.broadcastEvent(hub.Event{Seq: int64(i + 1), Ev: "agent.update"})
	}
	if _, ok := s.sessions[stuck]; ok {
		t.Fatal("overflowing session should leave the fanout set on first overflow")
	}
}
