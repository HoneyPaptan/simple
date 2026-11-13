package agents_test

import (
	"testing"

	"sh.simple/simpled/internal/agents"
)

func TestParseCursorModelList(t *testing.T) {
	sample := `Available models

composer-2.5 - Composer 2.5 (current)
auto - Auto (default)
gpt-5.3-codex-low - Codex 5.3 Low
`
	got := agents.ParseCursorModelList(sample)
	if len(got) != 3 {
		t.Fatalf("models %d", len(got))
	}
	if got[0].ID != "composer-2.5" || got[0].Label != "Composer 2.5" {
		t.Fatalf("first %#v", got[0])
	}
	if got[1].ID != "auto" || got[1].Label != "Auto (default)" {
		t.Fatalf("second %#v", got[1])
	}
}

func TestSimpleModeToCursorFlags(t *testing.T) {
	if len(agents.SimpleModeToCursor("plan")) != 1 {
		t.Fatal("plan")
	}
	if agents.SimpleModeToCursor("ask") != nil {
		t.Fatal("ask")
	}
}
