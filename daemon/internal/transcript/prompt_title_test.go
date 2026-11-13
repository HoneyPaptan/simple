package transcript_test

import (
	"os"
	"path/filepath"
	"testing"

	"sh.simple/simpled/internal/transcript"
)

func TestFirstUserPromptFromClaudeJSONL(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "s.jsonl")
	body := `{"type":"user","uuid":"u1","timestamp":"2026-01-01T12:00:00.000Z","message":{"role":"user","content":"Ship the folder picker"}}
{"type":"assistant","uuid":"a1","timestamp":"2026-01-01T12:00:01.000Z","message":{"role":"assistant","content":[{"type":"text","text":"Ok"}]}}
`
	if err := os.WriteFile(path, []byte(body), 0o644); err != nil {
		t.Fatal(err)
	}
	got, err := transcript.FirstUserPrompt(path, "claude")
	if err != nil {
		t.Fatal(err)
	}
	if got != "Ship the folder picker" {
		t.Fatalf("prompt %q", got)
	}
}
