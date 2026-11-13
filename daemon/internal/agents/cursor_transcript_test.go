package agents_test

import (
	"os"
	"path/filepath"
	"testing"
	"time"

	"sh.simple/simpled/internal/agents"
)

func TestCursorProjectSlug(t *testing.T) {
	if got := agents.CursorProjectSlug("/home/simple/projects/simple"); got != "home-simple-projects-simple" {
		t.Fatalf("slug %q", got)
	}
}

func TestFirstCursorTranscriptClaimsAndStartTime(t *testing.T) {
	home := t.TempDir()
	t.Setenv("HOME", home)
	cwd := filepath.Join(home, "proj")
	root := filepath.Join(agents.CursorProjectDir(cwd), "agent-transcripts")
	oldID := "11111111-1111-1111-1111-111111111111"
	newID := "22222222-2222-2222-2222-222222222222"
	oldDir := filepath.Join(root, oldID)
	newDir := filepath.Join(root, newID)
	for _, d := range []string{oldDir, newDir} {
		if err := os.MkdirAll(d, 0o755); err != nil {
			t.Fatal(err)
		}
	}
	oldPath := filepath.Join(oldDir, oldID+".jsonl")
	newPath := filepath.Join(newDir, newID+".jsonl")
	if err := os.WriteFile(oldPath, []byte("{}\n"), 0o644); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(newPath, []byte("{}\n"), 0o644); err != nil {
		t.Fatal(err)
	}
	oldInfo, _ := os.Stat(oldPath)
	start := oldInfo.ModTime().Add(-time.Second).UnixMilli()
	p, sid := agents.FirstCursorTranscript(cwd, start, map[string]struct{}{oldPath: {}})
	if p != newPath || sid != newID {
		t.Fatalf("pick %q %q", p, sid)
	}
	p, sid = agents.FirstCursorTranscript(cwd, start, map[string]struct{}{oldPath: {}, newPath: {}})
	if p != "" || sid != "" {
		t.Fatalf("all claimed still picked %q", p)
	}
}

func TestFirstCursorTranscriptPrefersEarliestAfterStart(t *testing.T) {
	home := t.TempDir()
	t.Setenv("HOME", home)
	cwd := filepath.Join(home, "proj")
	root := filepath.Join(agents.CursorProjectDir(cwd), "agent-transcripts")
	base := time.Now().Add(-time.Hour)
	ids := []string{"aaaaaaaa-0000-0000-0000-000000000001", "aaaaaaaa-0000-0000-0000-000000000002", "aaaaaaaa-0000-0000-0000-000000000003"}
	for i, id := range ids {
		dir := filepath.Join(root, id)
		if err := os.MkdirAll(dir, 0o755); err != nil {
			t.Fatal(err)
		}
		if err := os.WriteFile(filepath.Join(dir, id+".jsonl"), []byte("{}\n"), 0o644); err != nil {
			t.Fatal(err)
		}
		at := base.Add(time.Duration(i) * time.Minute)
		if err := os.Chtimes(dir, at, at); err != nil {
			t.Fatal(err)
		}
	}
	start := base.Add(30 * time.Second).UnixMilli()
	_, sid := agents.FirstCursorTranscript(cwd, start, nil)
	if sid != ids[1] {
		t.Fatalf("picked %s", sid)
	}
}
