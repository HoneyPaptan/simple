package transcript

import (
	"os"
	"path/filepath"
	"testing"
	"time"

	"sh.simple/simpled/internal/agents"
	"sh.simple/simpled/internal/wire"
)

func TestCursorPathForBindsNewestTranscript(t *testing.T) {
	home := t.TempDir()
	t.Setenv("HOME", home)
	cwd := filepath.Join(home, "ws")
	sid := "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
	dir := filepath.Join(agents.CursorProjectDir(cwd), "agent-transcripts", sid)
	if err := os.MkdirAll(dir, 0o755); err != nil {
		t.Fatal(err)
	}
	fp := filepath.Join(dir, sid+".jsonl")
	if err := os.WriteFile(fp, []byte(`{"role":"user","message":{"content":[{"type":"text","text":"hi"}]}}`+"\n"), 0o644); err != nil {
		t.Fatal(err)
	}
	m := NewManager(func(string, any) {})
	info, _ := os.Stat(fp)
	start := info.ModTime().Add(-time.Second).UnixMilli()
	got := m.PathForAgent("agent1", wire.Agent{Kind: "cursor", Cwd: cwd, StartedMs: start}, "")
	if got != fp {
		t.Fatalf("path %q", got)
	}
	got2 := m.PathForAgent("agent1", wire.Agent{Kind: "cursor", Cwd: cwd, StartedMs: start}, "")
	if got2 != fp {
		t.Fatalf("rebind %q", got2)
	}
}

func writeCursorChat(t *testing.T, cwd, sid string, when time.Time) string {
	t.Helper()
	dir := filepath.Join(agents.CursorProjectDir(cwd), "agent-transcripts", sid)
	if err := os.MkdirAll(dir, 0o755); err != nil {
		t.Fatal(err)
	}
	fp := filepath.Join(dir, sid+".jsonl")
	if err := os.WriteFile(fp, []byte(`{"role":"user","message":{"content":[{"type":"text","text":"hi"}]}}`+"\n"), 0o644); err != nil {
		t.Fatal(err)
	}
	for _, p := range []string{fp, dir} {
		if err := os.Chtimes(p, when, when); err != nil {
			t.Fatal(err)
		}
	}
	return fp
}

func TestCursorBindingMovesToNewerChat(t *testing.T) {
	home := t.TempDir()
	t.Setenv("HOME", home)
	cwd := filepath.Join(home, "ws")
	old := writeCursorChat(t, cwd, "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", time.Now().Add(-time.Hour))
	agent := wire.Agent{ID: "agent1", Kind: "cursor", Cwd: cwd, StartedMs: time.Now().Add(-2 * time.Hour).UnixMilli()}
	m := NewManager(func(string, any) {})
	m.SetAgentLookup(func(string) (wire.Agent, bool) { return agent, true })
	m.BindSession("agent1", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", cwd, old, "cursor")
	newer := writeCursorChat(t, cwd, "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", time.Now())
	if got := m.PathForAgent("agent1", agent, ""); got != newer {
		t.Fatalf("PathForAgent %q", got)
	}
	m.BindSession("agent1", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", cwd, old, "cursor")
	if got, _ := m.tailTarget("agent1"); got != newer {
		t.Fatalf("tailTarget %q", got)
	}
}
