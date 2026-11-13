package agents

import (
	"sort"
	"testing"

	"sh.simple/simpled/internal/hc"
	"sh.simple/simpled/internal/wire"
)

func TestPollableKeepsLiveDoneAgents(t *testing.T) {
	s := NewStore(func(string, any) {})
	s.byID["live-done"] = &wire.Agent{ID: "live-done", Status: "done"}
	s.byID["finished"] = &wire.Agent{ID: "finished", Status: "done"}
	s.byID["failed"] = &wire.Agent{ID: "failed", Status: "failed"}
	s.terms["live-done"] = hc.Terminal{State: "live"}
	s.terms["finished"] = hc.Terminal{State: "finished"}
	got := s.PollableAgentIDs()
	sort.Strings(got)
	if len(got) != 1 || got[0] != "live-done" {
		t.Fatalf("pollable %v", got)
	}
}
