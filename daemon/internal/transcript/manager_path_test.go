package transcript

import (
	"os"
	"path/filepath"
	"testing"

	"sh.simple/simpled/internal/agents"
	"sh.simple/simpled/internal/wire"
)

func TestShellAgentGetsNoGuessedClaudeTranscript(t *testing.T) {
	home := t.TempDir()
	t.Setenv("HOME", home)
	t.Setenv("CLAUDE_CONFIG_DIR", "")
	cwd := filepath.Join(home, "proj")
	dir := agents.ClaudeProjectDir(cwd)
	if err := os.MkdirAll(dir, 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(dir, "other.jsonl"), []byte("{}\n"), 0o644); err != nil {
		t.Fatal(err)
	}
	m := NewManager(func(string, any) {})
	if got := m.PathForAgent("t1", wire.Agent{Kind: "shell", Cwd: cwd}, ""); got != "" {
		t.Fatalf("shell agent got a claude transcript: %s", got)
	}
	if got := m.PathForAgent("t2", wire.Agent{Kind: "claude", Cwd: cwd}, ""); got == "" {
		t.Fatal("claude agent lost its transcript fallback")
	}
}

func TestClaudeSessionLabelFallsBackWhenFileMissing(t *testing.T) {
	home := t.TempDir()
	t.Setenv("HOME", home)
	t.Setenv("CLAUDE_CONFIG_DIR", "")
	cwd := filepath.Join(home, "proj")
	dir := agents.ClaudeProjectDir(cwd)
	if err := os.MkdirAll(dir, 0o755); err != nil {
		t.Fatal(err)
	}
	real := filepath.Join(dir, "real.jsonl")
	if err := os.WriteFile(real, []byte("{}\n"), 0o644); err != nil {
		t.Fatal(err)
	}
	session := "asked-for"
	m := NewManager(func(string, any) {})
	if got := m.PathForAgent("t1", wire.Agent{Kind: "claude", Cwd: cwd, Session: &session}, ""); got != real {
		t.Fatalf("path %q", got)
	}
}

func TestTailFollowsUnboundAgentThroughLookup(t *testing.T) {
	home := t.TempDir()
	t.Setenv("HOME", home)
	t.Setenv("CLAUDE_CONFIG_DIR", "")
	cwd := filepath.Join(home, "proj")
	dir := agents.ClaudeProjectDir(cwd)
	if err := os.MkdirAll(dir, 0o755); err != nil {
		t.Fatal(err)
	}
	session := "s1"
	line := `{"type":"assistant","uuid":"a1","timestamp":"2026-11-06T11:41:33Z","message":{"role":"assistant","content":[{"type":"text","text":"hi there"}]}}` + "\n"
	if err := os.WriteFile(filepath.Join(dir, session+".jsonl"), []byte(line), 0o644); err != nil {
		t.Fatal(err)
	}
	var got []wire.TranscriptAppend
	m := NewManager(func(ev string, data any) {
		if ev == "transcript.append" {
			got = append(got, data.(wire.TranscriptAppend))
		}
	})
	m.SetAgentLookup(func(id string) (wire.Agent, bool) {
		return wire.Agent{ID: id, Kind: "claude", Cwd: cwd, Session: &session}, id == "t1"
	})
	m.tailAgent("t1", map[string]int64{})
	if len(got) != 1 || len(got[0].Messages) == 0 {
		t.Fatalf("unbound agent got no transcript append: %+v", got)
	}
}

func TestHerdrClaudeAgentNeverBorrowsAnotherSession(t *testing.T) {
	home := t.TempDir()
	t.Setenv("HOME", home)
	t.Setenv("CLAUDE_CONFIG_DIR", "")
	cwd := filepath.Join(home, "proj")
	dir := agents.ClaudeProjectDir(cwd)
	if err := os.MkdirAll(dir, 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(dir, "other.jsonl"), []byte("{}\n"), 0o644); err != nil {
		t.Fatal(err)
	}
	m := NewManager(func(string, any) {})
	first, second := "s1", "s2"
	a := wire.Agent{ID: "h:w1:p2", Runtime: "herdr", Kind: "claude", Cwd: cwd, Session: &first}
	if got := m.PathForAgent(a.ID, a, ""); got != agents.TranscriptPath(first, cwd) {
		t.Fatalf("got %s", got)
	}
	m.BindSession(a.ID, first, cwd, agents.TranscriptPath(first, cwd), "claude")
	a.Session = &second
	if got := m.PathForAgent(a.ID, a, ""); got != agents.TranscriptPath(second, cwd) {
		t.Fatalf("new session kept old path: %s", got)
	}
}

func TestTranscriptBindingsSurviveRestart(t *testing.T) {
	dir := t.TempDir()
	state := filepath.Join(dir, "bindings.json")
	transcriptFile := filepath.Join(dir, "t.jsonl")
	if err := os.WriteFile(transcriptFile, []byte("{}\n"), 0o644); err != nil {
		t.Fatal(err)
	}
	first := NewManager(func(string, any) {})
	first.PersistBindings(state)
	first.BindSession("h:w1:p4", "s1", dir, transcriptFile, "cursor")
	second := NewManager(func(string, any) {})
	second.PersistBindings(state)
	if got := second.PathForAgent("h:w1:p4", wire.Agent{Kind: "cursor", Cwd: dir, StartedMs: 1 << 50}, ""); got != transcriptFile {
		t.Fatalf("got %q", got)
	}
}
