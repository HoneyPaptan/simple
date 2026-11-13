package agents

import (
	"path/filepath"
	"testing"

	"sh.simple/simpled/internal/herdr"
)

func TestHerdrSessionSurvivesRestart(t *testing.T) {
	path := filepath.Join(t.TempDir(), "herdr-agents.json")
	info := herdr.AgentInfo{Agent: "opencode", PaneID: "w1:p2", Cwd: t.TempDir(), AgentStatus: "idle"}
	first := NewStore(func(string, any) {})
	first.PersistHerdr(path)
	first.LoadHerdrAgents([]herdr.AgentInfo{info})
	id := HerdrAgentID(info.PaneID)
	session := "ses_e3e4886ecffeA7Ns3Y1t6nrw35"
	first.mu.Lock()
	first.byID[id].Session = &session
	first.byID[id].StartedMs = 1234
	first.mu.Unlock()
	first.LoadHerdrAgents([]herdr.AgentInfo{info})

	second := NewStore(func(string, any) {})
	second.PersistHerdr(path)
	second.LoadHerdrAgents([]herdr.AgentInfo{info})
	got, ok := second.Get(id)
	if !ok || got.Session == nil || *got.Session != session || got.StartedMs != 1234 {
		t.Fatalf("restored agent = %+v, want session %s and start 1234", got, session)
	}
}

func TestHerdrStatusTimeSurvivesRestart(t *testing.T) {
	path := filepath.Join(t.TempDir(), "herdr-agents.json")
	info := herdr.AgentInfo{Agent: "cursor", PaneID: "w1:p3", Cwd: t.TempDir(), AgentStatus: "done"}
	first := NewStore(func(string, any) {})
	first.PersistHerdr(path)
	first.LoadHerdrAgents([]herdr.AgentInfo{info})
	id := HerdrAgentID(info.PaneID)
	first.mu.Lock()
	first.byID[id].UpdatedMs = 4321
	first.mu.Unlock()
	first.LoadHerdrAgents([]herdr.AgentInfo{info})

	second := NewStore(func(string, any) {})
	second.PersistHerdr(path)
	second.LoadHerdrAgents([]herdr.AgentInfo{info})
	if got, _ := second.Get(id); got.UpdatedMs != 4321 {
		t.Fatalf("updated %d, want 4321", got.UpdatedMs)
	}
	info.AgentStatus = "working"
	third := NewStore(func(string, any) {})
	third.PersistHerdr(path)
	third.LoadHerdrAgents([]herdr.AgentInfo{info})
	if got, _ := third.Get(id); got.UpdatedMs == 4321 {
		t.Fatal("status changed while down but kept the old time")
	}
}
