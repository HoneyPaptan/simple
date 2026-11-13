package transcript_test

import (
	"os"
	"path/filepath"
	"testing"

	"sh.simple/simpled/internal/transcript"
	"sh.simple/simpled/internal/wire"
)

func toolByID(t *testing.T, msgs []wire.Message, id string) *wire.Tool {
	t.Helper()
	for _, m := range msgs {
		if m.ID == id && m.Tool != nil {
			return m.Tool
		}
	}
	t.Fatalf("tool %s missing", id)
	return nil
}

func TestClaudeToolStatusComesFromIsError(t *testing.T) {
	msgs, err := transcript.ParseClaudeJSONL(filepath.Join("testdata", "tools.jsonl"))
	if err != nil {
		t.Fatal(err)
	}
	bash := toolByID(t, msgs, "tb")
	if bash.Status != "error" || bash.Output == nil || *bash.Output != "FAIL pkg" || bash.Summary != "Run the tests" {
		t.Fatalf("bash %+v", bash)
	}
	read := toolByID(t, msgs, "tr")
	if read.Status != "ok" || read.Summary != "error_handling.go" {
		t.Fatalf("read %+v", read)
	}
}

func TestClaudeDeniedToolIsMarkedDenied(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "s.jsonl")
	lines := `{"type":"assistant","uuid":"a1","timestamp":"2026-11-10T10:05:50Z","message":{"role":"assistant","content":[{"type":"tool_use","id":"td","name":"Bash","input":{"command":"magick x.jpg crop.png","description":"Crop"}}]}}
{"type":"user","uuid":"u1","timestamp":"2026-11-10T10:05:57Z","toolDenialKind":"permission-rule","message":{"role":"user","content":[{"type":"tool_result","content":"Permission denied by hook","is_error":true,"tool_use_id":"td"}]}}
`
	if err := os.WriteFile(path, []byte(lines), 0o644); err != nil {
		t.Fatal(err)
	}
	msgs, err := transcript.ParseClaudeJSONL(path)
	if err != nil {
		t.Fatal(err)
	}
	if tool := toolByID(t, msgs, "td"); tool.Status != "denied" {
		t.Fatalf("status %q", tool.Status)
	}
}
