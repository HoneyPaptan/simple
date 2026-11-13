package detect

import (
	"os"
	"strings"
	"testing"
)

func TestTerminalPromptScreens(t *testing.T) {
	cases := []struct {
		kind string
		file string
	}{
		{"gemini", "testdata/gemini_tool_yesno.txt"},
		{"copilot", "testdata/copilot_tool_yesno.txt"},
		{"crush", "testdata/crush_permission.txt"},
		{"kimi", "testdata/kimi_approve_choice.txt"},
	}
	for _, c := range cases {
		b, err := os.ReadFile(c.file)
		if err != nil {
			t.Fatal(err)
		}
		got := ParseForKind(c.kind, string(b))
		if got == nil {
			t.Fatalf("%s: no result for %s", c.kind, c.file)
		}
	}
}

func TestClaudeWritePromptFromHerdrScreen(t *testing.T) {
	raw, err := os.ReadFile("testdata/claude_herdr_write_prompt.txt")
	if err != nil {
		t.Fatal(err)
	}
	r := ParseForKind("herdr", string(raw))
	if r == nil || len(r.Options) != 3 || !strings.Contains(r.Title, "e2e-herdr.txt") {
		t.Fatalf("got %+v", r)
	}
}

func TestCursorDoneScreenInHerdrIsNotAPrompt(t *testing.T) {
	raw, err := os.ReadFile("testdata/cursor_herdr_done.txt")
	if err != nil {
		t.Fatal(err)
	}
	if r := ParseForKind("herdr", string(raw)); r != nil {
		t.Fatalf("got %+v", r)
	}
}
