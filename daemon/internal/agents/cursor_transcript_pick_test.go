package agents

import (
	"testing"
	"time"
)

func TestNewerCursorTranscriptMovesToANewChat(t *testing.T) {
	t0 := time.Unix(1000, 0)
	old := CursorTranscript{Path: "a", Session: "a", Created: t0, Modified: t0.Add(time.Minute)}
	fresh := CursorTranscript{Path: "b", Session: "b", Created: t0.Add(2 * time.Minute), Modified: t0.Add(3 * time.Minute)}
	other := CursorTranscript{Path: "c", Session: "c", Created: t0.Add(2 * time.Minute), Modified: t0.Add(4 * time.Minute)}
	got, ok := NewerCursorTranscript([]CursorTranscript{old, fresh, other}, "a", map[string]struct{}{"c": {}})
	if !ok || got.Path != "b" {
		t.Fatalf("got %+v ok=%v, want b", got, ok)
	}
	if _, ok := NewerCursorTranscript([]CursorTranscript{old, fresh}, "b", nil); ok {
		t.Fatal("an older chat must not replace the current one")
	}
}

func TestLatestCursorTranscriptSkipsClaimed(t *testing.T) {
	t0 := time.Unix(1000, 0)
	a := CursorTranscript{Path: "a", Modified: t0}
	b := CursorTranscript{Path: "b", Modified: t0.Add(time.Minute)}
	got, ok := LatestCursorTranscript([]CursorTranscript{a, b}, map[string]struct{}{"b": {}})
	if !ok || got.Path != "a" {
		t.Fatalf("got %+v, want a", got)
	}
}
