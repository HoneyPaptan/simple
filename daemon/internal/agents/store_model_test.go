package agents

import (
	"testing"

	"sh.simple/simpled/internal/hc"
	"sh.simple/simpled/internal/wire"
)

func TestOpenCodeModelSurvivesTerminalUpdate(t *testing.T) {
	s := NewStore(func(string, any) {})
	term := hc.Terminal{Address: "t9", Cwd: t.TempDir(), State: "live", Labels: map[string]string{"simple.kind": "opencode", "simple.model": "default", "simple.session": "ses_x"}}
	s.mu.Lock()
	s.upsertLocked(term)
	s.mu.Unlock()
	s.SetAgentModel("t9", "opencode/big-pickle")
	s.mu.Lock()
	s.upsertLocked(term)
	s.mu.Unlock()
	a, _ := s.Get("t9")
	if a.Model == nil || *a.Model != "opencode/big-pickle" {
		t.Fatalf("model reset to %v", a.Model)
	}
}

func TestContextUpdateKeepsListOrder(t *testing.T) {
	s := NewStore(func(string, any) {})
	term := hc.Terminal{Address: "t8", Cwd: t.TempDir(), State: "live", Labels: map[string]string{"simple.kind": "claude"}}
	s.mu.Lock()
	s.upsertLocked(term)
	s.mu.Unlock()
	before, _ := s.Get("t8")
	s.SetAgentContext("t8", &wire.AgentContext{Used: 10, Window: 100})
	after, _ := s.Get("t8")
	if after.UpdatedMs != before.UpdatedMs || after.Context == nil {
		t.Fatalf("context update moved activity time: %d to %d", before.UpdatedMs, after.UpdatedMs)
	}
}

func TestSetAgentFieldsKeepsTimeWhenUnchanged(t *testing.T) {
	s := NewStore(func(string, any) {})
	s.Upsert(wire.Agent{ID: "t1", Kind: "cursor", Status: "unknown", StartedMs: 1000, UpdatedMs: 1000})
	s.SetAgentFields("t1", "unknown", nil)
	got, _ := s.Get("t1")
	if got.UpdatedMs != 1000 || got.Status != "unknown" {
		t.Fatalf("same status stamped %s %d", got.Status, got.UpdatedMs)
	}
	s.mu.Lock()
	s.byID["t1"].UpdatedMs = 1000
	s.mu.Unlock()
	s.SetAgentFields("t1", "working", nil)
	got, _ = s.Get("t1")
	if got.Status != "working" || got.UpdatedMs == 1000 {
		t.Fatalf("status change did not stamp: %s %d", got.Status, got.UpdatedMs)
	}
	s.mu.Lock()
	s.byID["t1"].UpdatedMs = 2000
	s.mu.Unlock()
	s.SetAgentFields("t1", "working", nil)
	got, _ = s.Get("t1")
	if got.UpdatedMs != 2000 || got.Status != "working" {
		t.Fatalf("repeat working stamped %d", got.UpdatedMs)
	}
	msg := "Continue?"
	s.SetAgentFields("t1", "waiting", &msg)
	got, _ = s.Get("t1")
	if got.Status != "waiting" || got.Waiting == nil || *got.Waiting != msg || got.UpdatedMs == 2000 {
		t.Fatalf("waiting change did not stamp: status %s updated %d", got.Status, got.UpdatedMs)
	}
	s.mu.Lock()
	s.byID["t1"].UpdatedMs = 3000
	s.mu.Unlock()
	again := "Continue?"
	s.SetAgentFields("t1", "waiting", &again)
	got, _ = s.Get("t1")
	if got.UpdatedMs != 3000 || got.Waiting == nil || *got.Waiting != again {
		t.Fatalf("same waiting stamped %d", got.UpdatedMs)
	}
	other := "Pick one"
	s.SetAgentFields("t1", "waiting", &other)
	got, _ = s.Get("t1")
	if got.UpdatedMs == 3000 || got.Waiting == nil || *got.Waiting != other {
		t.Fatalf("new waiting text did not stamp: %d %v", got.UpdatedMs, got.Waiting)
	}
}
