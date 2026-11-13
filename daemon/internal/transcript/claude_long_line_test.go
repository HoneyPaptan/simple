package transcript

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestParseClaudeJSONLHandlesLinesOverOneMegabyte(t *testing.T) {
	big := strings.Repeat("x", 2*1024*1024)
	body := `{"type":"user","uuid":"u1","timestamp":"2026-11-11T10:00:00Z","message":{"role":"user","content":"` + big + `"}}` + "\n" +
		`{"type":"assistant","uuid":"a1","timestamp":"2026-11-11T10:00:01Z","message":{"role":"assistant","content":[{"type":"text","text":"done"}]}}` + "\n"
	path := filepath.Join(t.TempDir(), "s.jsonl")
	if err := os.WriteFile(path, []byte(body), 0o644); err != nil {
		t.Fatal(err)
	}
	msgs, err := ParseClaudeJSONL(path)
	if err != nil {
		t.Fatalf("parse: %v", err)
	}
	if len(msgs) == 0 || msgs[len(msgs)-1].Text == nil || *msgs[len(msgs)-1].Text != "done" {
		t.Fatalf("last message not parsed: %d messages", len(msgs))
	}
}
