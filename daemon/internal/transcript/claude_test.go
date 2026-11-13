package transcript_test

import (
	"path/filepath"
	"runtime"
	"testing"

	"sh.simple/simpled/internal/transcript"
)

func TestParseMinimalFixture(t *testing.T) {
	_, file, _, ok := runtime.Caller(0)
	if !ok {
		t.Fatal("caller")
	}
	path := filepath.Join(filepath.Dir(file), "testdata", "minimal.jsonl")
	msgs, err := transcript.ParseClaudeJSONL(path)
	if err != nil {
		t.Fatal(err)
	}
	if len(msgs) < 2 {
		t.Fatalf("messages %d", len(msgs))
	}
}
