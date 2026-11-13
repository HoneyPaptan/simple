package transcript

import (
	"testing"

	"sh.simple/simpled/internal/wire"
)

func TestContextStaleOnlyWhenFileOrModelChanges(t *testing.T) {
	m := NewManager(func(string, any) {})
	opus := "opus"
	sonnet := "sonnet"
	a := wire.Agent{Model: &opus}
	steps := []struct {
		size  int64
		model *string
		want  bool
	}{
		{100, &opus, true},
		{100, &opus, false},
		{100, &opus, false},
		{120, &opus, true},
		{120, &sonnet, true},
		{120, &sonnet, false},
	}
	for i, s := range steps {
		a.Model = s.model
		if got := m.contextStale("a1", a, "/t/a.jsonl", s.size); got != s.want {
			t.Fatalf("step %d: contextStale = %v, want %v", i, got, s.want)
		}
	}
	if !m.contextStale("a2", a, "/t/a.jsonl", 120) {
		t.Fatal("a second agent on the same file must sync once")
	}
}
