package transcript_test

import (
	"encoding/json"
	"testing"

	"sh.simple/simpled/internal/transcript"
	"sh.simple/simpled/internal/wire"
)

func TestAppendOpenCodeMessage(t *testing.T) {
	var append wire.TranscriptAppend
	mgr := transcript.NewManager(func(ev string, payload any) {
		if ev != "transcript.append" {
			t.Fatalf("event %s", ev)
		}
		append = payload.(wire.TranscriptAppend)
	})
	mgr.BindSession("t1", "s1", "/tmp", "", "opencode")
	body, _ := json.Marshal(map[string]any{
		"id":   "m1",
		"role": "assistant",
		"part": map[string]any{"text": "Hello from OpenCode"},
	})
	mgr.AppendOpenCode("t1", body)
	if append.ID != "t1" || len(append.Messages) != 1 {
		t.Fatalf("append %#v", append)
	}
	if append.Messages[0].Text == nil || *append.Messages[0].Text != "Hello from OpenCode" {
		t.Fatalf("text %#v", append.Messages[0])
	}
	dup, _ := json.Marshal(map[string]any{
		"id":   "m1",
		"role": "assistant",
		"part": map[string]any{"text": "Hello from OpenCode"},
	})
	mgr.AppendOpenCode("t1", dup)
	if len(append.Messages) != 1 {
		t.Fatalf("dedupe %d", len(append.Messages))
	}
}
