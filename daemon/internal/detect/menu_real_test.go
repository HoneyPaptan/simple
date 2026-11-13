package detect

import (
	"os"
	"strings"
	"testing"
)

func TestClaudeTrustMenuRealScreen(t *testing.T) {
	b, err := os.ReadFile("testdata/claude_trust_real.txt")
	if err != nil {
		t.Fatal(err)
	}
	got := ParseForKind("claude", string(b))
	if got == nil {
		t.Fatal("no result")
	}
	it := got
	t.Logf("%+v", it)
	if len(it.Options) != 2 || it.Options[1].Label != "Yes, I trust this folder" {
		t.Fatalf("options %+v", it.Options)
	}
	want := "Accessing workspace:\n/home/simple/projects/simple\nClaude Code'll be able to read, edit, and execute files here.\nSecurity guide"
	if it.Body == nil || *it.Body != want {
		t.Fatalf("body %q", *it.Body)
	}
}

func TestClaudeSecurityNotesGate(t *testing.T) {
	b, err := os.ReadFile("testdata/claude_notes_real.txt")
	if err != nil {
		t.Fatal(err)
	}
	got := ParseForKind("claude", string(b))
	if got == nil || got.Detector != "continue" {
		t.Fatalf("got %+v", got)
	}
	if ParseForKind("claude", "Done. Press Enter to continue in the docs.\n\n❯ \n") != nil {
		t.Fatal("matched a normal input screen")
	}
}

func TestClaudeAutoModeMenu(t *testing.T) {
	b, err := os.ReadFile("testdata/claude_auto_mode_real.txt")
	if err != nil {
		t.Fatal(err)
	}
	got := ParseForKind("claude", string(b))
	if got == nil || len(got.Options) != 2 || got.Options[1].Label != "No, keep bypass permissions" {
		t.Fatalf("got %+v", got)
	}
	if got.Title != "Make auto mode your default permission mode?" {
		t.Fatalf("title %q", got.Title)
	}
	if got.Body == nil || strings.Contains(*got.Body, "MCP") || !strings.HasPrefix(*got.Body, "Auto mode lets Claude") {
		t.Fatalf("body %v", *got.Body)
	}
}

func TestClaudeIdleInputIsNotAMenu(t *testing.T) {
	screen := "────────\n❯ Try \"fix lint errors\"\n────────\n  ⏸ manual mode on\n"
	if r := ParseForKind("claude", screen); r != nil {
		t.Fatalf("got %+v", r)
	}
}

func TestCursorIdleScreenIsNotAPrompt(t *testing.T) {
	b, err := os.ReadFile("testdata/cursor_idle_real.txt")
	if err != nil {
		t.Fatal(err)
	}
	if r := ParseForKind("cursor", string(b)); r != nil {
		t.Fatalf("got %+v", r)
	}
}
