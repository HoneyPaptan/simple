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
