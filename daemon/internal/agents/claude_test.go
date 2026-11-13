package agents

import (
	"os"
	"path/filepath"
	"testing"
)

func TestTranscriptPathFindsSessionInAnotherClaudeConfigDir(t *testing.T) {
	home := t.TempDir()
	t.Setenv("HOME", home)
	t.Setenv("CLAUDE_CONFIG_DIR", "")
	dir := filepath.Join(home, ".claude-personal", "projects", "-home-u--odd-path")
	if err := os.MkdirAll(dir, 0o755); err != nil {
		t.Fatal(err)
	}
	want := filepath.Join(dir, "abc-123.jsonl")
	if err := os.WriteFile(want, []byte("{}\n"), 0o644); err != nil {
		t.Fatal(err)
	}
	if got := TranscriptPath("abc-123", "/home/u/.odd/path"); got != want {
		t.Fatalf("TranscriptPath = %q, want %q", got, want)
	}
}
