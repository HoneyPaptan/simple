package agents

import (
	"testing"

	"sh.simple/simpled/internal/hc"
	"sh.simple/simpled/internal/herdr"
	"sh.simple/simpled/internal/wire"
)

func TestHerdrAgentMapping(t *testing.T) {
	id := HerdrAgentID("w1:p2")
	if !IsHerdrAgentID(id) {
		t.Fatal("expected herdr id")
	}
	info := herdr.AgentInfo{
		Agent: "claude", AgentStatus: "blocked", PaneID: "w1:p2",
		Name: "rev", Cwd: "/tmp/proj", TerminalTitleStripped: "ignored",
		AgentSession: &herdr.AgentSession{Kind: "id", Value: "sess-1", Agent: "claude"},
	}
	a := buildHerdrAgent(info)
	if a.Kind != "claude" || a.Status != "waiting" || a.Runtime != "herdr" {
		t.Fatalf("%+v", a)
	}
	if a.Session == nil || *a.Session != "sess-1" {
		t.Fatal("session")
	}
}

func TestHCSnapshotKeepsHerdrAgentsAndDropsGoneTerminals(t *testing.T) {
	var removed []string
	s := NewStore(func(ev string, data any) {
		if r, ok := data.(wire.AgentRemoved); ok && ev == "agent.removed" {
			removed = append(removed, r.ID)
		}
	})
	s.LoadSnapshot([]hc.Terminal{{Address: "t1", Argv: []string{"bash"}, State: "running"}})
	s.LoadHerdrAgents([]herdr.AgentInfo{{Agent: "claude", AgentStatus: "idle", PaneID: "w1:p2", Cwd: "/tmp"}})
	s.LoadSnapshot(nil)
	if len(removed) != 1 || removed[0] != "t1" {
		t.Fatalf("removed %v", removed)
	}
	if _, ok := s.Get("t1"); ok {
		t.Fatal("gone hc terminal still listed")
	}
	if _, ok := s.Get(HerdrAgentID("w1:p2")); !ok {
		t.Fatal("herdr agent dropped by hc snapshot")
	}
}

func TestAgentRecencyComesFromTerminalNotDaemonStart(t *testing.T) {
	s := NewStore(func(string, any) {})
	statusMs := uint64(5000)
	s.LoadSnapshot([]hc.Terminal{{Address: "t1", Argv: []string{"claude"}, State: "live", CreatedMs: 1000, StatusMs: &statusMs}})
	a, _ := s.Get("t1")
	if a.UpdatedMs != 5000 {
		t.Fatalf("updated %d", a.UpdatedMs)
	}
	s.LoadHerdrAgents([]herdr.AgentInfo{{Agent: "claude", AgentStatus: "idle", PaneID: "w1:p2", Cwd: "/tmp", Revision: 1}})
	first, _ := s.Get(HerdrAgentID("w1:p2"))
	s.LoadHerdrAgents([]herdr.AgentInfo{{Agent: "claude", AgentStatus: "idle", PaneID: "w1:p2", Cwd: "/tmp", Revision: 2}})
	again, _ := s.Get(HerdrAgentID("w1:p2"))
	if again.UpdatedMs != first.UpdatedMs {
		t.Fatal("herdr agent moved without a status change")
	}
}

func TestStartedHerdrAgentTitleSkipsGeneratedName(t *testing.T) {
	a := buildHerdrAgent(herdr.AgentInfo{Agent: "claude", PaneID: "w1:p3", Name: "scbdb4292", TerminalTitleStripped: "Fix login"})
	if a.Title != "Fix login" {
		t.Fatalf("title %q", a.Title)
	}
	b := buildHerdrAgent(herdr.AgentInfo{Agent: "claude", PaneID: "w1:p4", Name: "reviewer"})
	if b.Title != "reviewer" {
		t.Fatalf("title %q", b.Title)
	}
}

func TestClosedHerdrPaneLeavesStoreAndWorkspaceCount(t *testing.T) {
	s := NewStore(func(string, any) {})
	s.LoadHerdrAgents([]herdr.AgentInfo{
		{Agent: "claude", AgentStatus: "idle", PaneID: "w1:p2", Cwd: "/tmp"},
		{Agent: "cursor", AgentStatus: "idle", PaneID: "w1:p3", Cwd: "/tmp"},
	})
	s.LoadHerdrAgents([]herdr.AgentInfo{{Agent: "claude", AgentStatus: "idle", PaneID: "w1:p2", Cwd: "/tmp"}})
	if _, ok := s.Get(HerdrAgentID("w1:p3")); ok {
		t.Fatal("closed pane still listed")
	}
	if n := len(s.Snapshot()); n != 1 {
		t.Fatalf("snapshot has %d agents", n)
	}
}

func TestHerdrAgentKeepsSessionSetAtStart(t *testing.T) {
	s := NewStore(func(string, any) {})
	session := "ses_1"
	s.Upsert(wire.Agent{ID: HerdrAgentID("w1:p7"), Runtime: "herdr", Kind: "opencode", Session: &session})
	s.LoadHerdrAgents([]herdr.AgentInfo{{Agent: "opencode", AgentStatus: "idle", PaneID: "w1:p7", Cwd: "/tmp"}})
	a, _ := s.Get(HerdrAgentID("w1:p7"))
	if a.Session == nil || *a.Session != session {
		t.Fatalf("session %v", a.Session)
	}
	if got := sessionArg([]string{"--session", "x", "/tmp"}); got == nil || *got != "x" {
		t.Fatal("session arg")
	}
}

func TestHerdrCwdDropsDeletedMarker(t *testing.T) {
	if got := herdrCwd("/home/u/projects/app-w2 (deleted)"); got != "/home/u/projects/app-w2" {
		t.Fatalf("herdrCwd = %q", got)
	}
	a := buildHerdrAgent(herdr.AgentInfo{PaneID: "w1:p1", Agent: "cursor", Cwd: "/tmp/gone (deleted)"})
	if a.Cwd != "/tmp/gone" || a.Workspace != "/tmp/gone" {
		t.Fatalf("cwd %q workspace %q", a.Cwd, a.Workspace)
	}
}
