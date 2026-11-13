package agents

import (
	"strings"
	"testing"

	"sh.simple/simpled/internal/hc"
)

func TestCompactPromptTitleUsesFirstLine(t *testing.T) {
	got := CompactPromptTitle("Fix the header\nsecond line")
	if got != "Fix the header" {
		t.Fatalf("title %q", got)
	}
}

func TestCompactPromptTitleTruncatesWithEllipsis(t *testing.T) {
	in := strings.Repeat("a", 50)
	got := CompactPromptTitle(in)
	want := in[:promptTitleMax-1] + "…"
	if got != want {
		t.Fatalf("title %q want %q", got, want)
	}
}

func TestPromptTitleFallbackOnGenericClaudeTitle(t *testing.T) {
	s := NewStore(func(string, any) {})
	s.LoadSnapshot([]hc.Terminal{{
		Address: "t1",
		Argv:    []string{"claude"},
		Cwd:     "/tmp",
		State:   "live",
		Labels:  map[string]string{"simple.kind": "claude"},
	}})
	if got := s.Snapshot()[0].Title; got != "Claude" {
		t.Fatalf("title %q", got)
	}
	s.SetAgentPromptTitle("t1", "Add workspace picker to home")
	a, _ := s.Get("t1")
	if a.Title != "Add workspace picker to home" {
		t.Fatalf("title %q", a.Title)
	}
}

func TestSessionTitleBeatsPromptFallback(t *testing.T) {
	s := NewStore(func(string, any) {})
	s.SetAgentPromptTitle("t1", "ignored until load")
	s.LoadSnapshot([]hc.Terminal{{
		Address:  "t1",
		Argv:     []string{"claude"},
		Cwd:      "/tmp",
		State:    "live",
		Labels:   map[string]string{"simple.kind": "claude"},
		Status:   &hc.StatusRecord{Title: "Release checklist"},
	}})
	a, _ := s.Get("t1")
	if a.Title != "Release checklist" {
		t.Fatalf("title %q", a.Title)
	}
}
