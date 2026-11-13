package transcript_test

import (
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"testing"

	"sh.simple/simpled/internal/transcript"
)

func TestParseCursorTranscriptFixture(t *testing.T) {
	_, file, _, ok := runtime.Caller(0)
	if !ok {
		t.Fatal("caller")
	}
	path := filepath.Join(filepath.Dir(file), "testdata", "cursor_transcript.jsonl")
	msgs, err := transcript.ParseCursorJSONL(path)
	if err != nil {
		t.Fatal(err)
	}
	if len(msgs) < 4 {
		t.Fatalf("messages %d", len(msgs))
	}
	if msgs[0].Role != "user" || msgs[0].Text == nil {
		t.Fatalf("first %#v", msgs[0])
	}
	if strings.Contains(*msgs[0].Text, "<user_query>") || strings.Contains(*msgs[0].Text, "timestamp") {
		t.Fatalf("user tags leaked: %q", *msgs[0].Text)
	}
	if !strings.Contains(*msgs[0].Text, "Read README.md") {
		t.Fatal("user body")
	}
	if msgs[1].Role != "assistant" || msgs[1].Text == nil || strings.Contains(*msgs[1].Text, "REDACTED") {
		t.Fatalf("assistant lead %#v", msgs[1])
	}
	if msgs[2].Role != "tool" || msgs[2].Tool == nil || msgs[2].Tool.Name != "Read" || msgs[2].Tool.Summary != "README.md" {
		t.Fatalf("read tool %#v", msgs[2])
	}
	if msgs[3].Role != "tool" || msgs[3].Tool == nil || msgs[3].Tool.Name != "Write" || msgs[3].Tool.Summary != "e2e-test.txt" {
		t.Fatalf("write tool %#v", msgs[3])
	}
	last := msgs[len(msgs)-1]
	if last.Role != "assistant" || last.Text == nil || !strings.Contains(*last.Text, "first heading") {
		t.Fatalf("final %#v", last)
	}
}

func TestCursorTaskResultPromptIsHidden(t *testing.T) {
	path := filepath.Join(t.TempDir(), "t.jsonl")
	body := `{"role":"user","message":{"content":[{"type":"text","text":"<user_query>fix it</user_query>"}]}}
{"role":"assistant","message":{"content":[{"type":"text","text":"done"}]}}
{"role":"user","message":{"content":[{"type":"text","text":"<user_query>Briefly inform the user about the task result and perform any follow-up actions (if needed). If there's no follow-ups needed, don't explicitly say that.</user_query>"}]}}
{"role":"assistant","message":{"content":[{"type":"text","text":"all good"}]}}
`
	if err := os.WriteFile(path, []byte(body), 0o600); err != nil {
		t.Fatal(err)
	}
	msgs, err := transcript.ParseCursorJSONL(path)
	if err != nil {
		t.Fatal(err)
	}
	users := 0
	for _, m := range msgs {
		if m.Role == "user" {
			users++
		}
	}
	if users != 1 {
		t.Fatalf("users %d in %#v", users, msgs)
	}
}
