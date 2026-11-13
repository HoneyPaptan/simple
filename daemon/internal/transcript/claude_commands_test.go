package transcript

import (
	"os"
	"path/filepath"
	"testing"
)

func TestClaudeSlashCommandsShowCommandAndOutput(t *testing.T) {
	lines := `{"type":"system","subtype":"local_command","uuid":"a","timestamp":"2026-11-09T11:34:12.132Z","content":"<command-name>/context</command-name>\n<command-message>context</command-message>\n<command-args></command-args>"}
{"type":"system","subtype":"local_command","uuid":"b","timestamp":"2026-11-09T11:34:12.131Z","content":"<local-command-stdout> \u001b[1mContext Usage\u001b[22m\n64k tokens</local-command-stdout>"}
{"type":"user","uuid":"c","timestamp":"2026-11-09T11:34:12.134Z","isMeta":true,"message":{"role":"user","content":"## Context Usage"}}
{"type":"user","uuid":"d","timestamp":"2026-11-09T11:35:00.000Z","message":{"role":"user","content":"<command-message>review</command-message>\n<command-name>/review</command-name>\n<command-args>main</command-args>"}}
`
	path := filepath.Join(t.TempDir(), "s.jsonl")
	if err := os.WriteFile(path, []byte(lines), 0o600); err != nil {
		t.Fatal(err)
	}
	got, err := ParseClaudeJSONL(path)
	if err != nil {
		t.Fatal(err)
	}
	if len(got) != 3 {
		t.Fatalf("got %d messages", len(got))
	}
	if got[0].Role != "user" || *got[0].Text != "/context" {
		t.Fatalf("command %+v", got[0])
	}
	if got[1].Role != "assistant" || *got[1].Text != "```\n Context Usage\n64k tokens\n```" {
		t.Fatalf("output %q", *got[1].Text)
	}
	if !(got[0].Ts < got[1].Ts && got[1].Ts < got[2].Ts) {
		t.Fatalf("not in file order: %d %d %d", got[0].Ts, got[1].Ts, got[2].Ts)
	}
	if got[2].Role != "user" || *got[2].Text != "/review main" {
		t.Fatalf("prompt command %+v", got[2])
	}
}
