package transcript_test

import (
	"path/filepath"
	"runtime"
	"testing"

	"sh.simple/simpled/internal/transcript"
)

func TestParseCodexRolloutFixture(t *testing.T) {
	_, file, _, ok := runtime.Caller(0)
	if !ok {
		t.Fatal("caller")
	}
	path := filepath.Join(filepath.Dir(file), "testdata", "codex_rollout.jsonl")
	msgs, err := transcript.ParseCodexJSONL(path)
	if err != nil {
		t.Fatal(err)
	}
	if len(msgs) != 4 {
		t.Fatalf("messages %d", len(msgs))
	}
	if msgs[0].Role != "user" || msgs[0].Text == nil || *msgs[0].Text != "Plan the cache layer" {
		t.Fatalf("first %#v", msgs[0])
	}
	if msgs[1].Role != "assistant" {
		t.Fatalf("second role %s", msgs[1].Role)
	}
}
