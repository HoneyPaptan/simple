package opencode

import (
	"encoding/json"
	"os"
	"testing"
)

func loadItems(t *testing.T) []Item {
	t.Helper()
	raw, err := os.ReadFile("testdata/messages.json")
	if err != nil {
		t.Fatal(err)
	}
	var p messagePage
	if err := json.Unmarshal(raw, &p); err != nil {
		t.Fatal(err)
	}
	return p.Data
}

func TestToWireKeepsTextToolsAndThinking(t *testing.T) {
	msgs := ToWire(loadItems(t))
	if last := msgs[len(msgs)-1]; *last.Text != "OpenCode stopped: Free usage limit reached." {
		t.Fatalf("failed turn not shown: %+v", last)
	}
	msgs = msgs[:len(msgs)-1]
	if msgs[1].Role != "thinking" || *msgs[1].Text != "hidden" {
		t.Fatalf("reasoning row wrong: %+v", msgs[1])
	}
	msgs = append(msgs[:1], msgs[2:]...)
	if len(msgs) != 4 {
		t.Fatalf("got %d messages: %+v", len(msgs), msgs)
	}
	if msgs[0].Role != "user" || *msgs[0].Text != "fix it" || *msgs[1].Text != "Looking." {
		t.Fatalf("text rows wrong: %+v %+v", msgs[0], msgs[1])
	}
	bash := msgs[2].Tool
	if bash.Name != "bash" || bash.Status != "ok" || bash.Summary != "Run tests" || *bash.Output != "ok" {
		t.Fatalf("bash %+v", bash)
	}
	edit := msgs[3].Tool
	if edit.Status != "error" || *edit.Output != "no match" || edit.Diff == nil || edit.Summary != "main.go" {
		t.Fatalf("edit %+v", edit)
	}
}

func TestBusyUntilIdle(t *testing.T) {
	items := loadItems(t)
	if Busy(items) {
		t.Fatal("idle session reported busy")
	}
	if !Busy(items[:1]) {
		t.Fatal("fresh prompt not busy")
	}
}

func TestLastReplyPartCarriesCompletionTime(t *testing.T) {
	items := []Item{
		{ID: "u", Type: "user", Text: "go", Time: Times{Created: 1000}},
		{ID: "a", Type: "assistant", Time: Times{Created: 1100, Completed: 42000}, Content: []Block{
			{Type: "tool", ID: "t1", Name: "bash", State: toolState{Status: "completed"}},
			{Type: "text", Text: "Done."},
		}},
	}
	msgs := ToWire(items)
	if len(msgs) != 3 {
		t.Fatalf("got %d messages", len(msgs))
	}
	if msgs[1].Ts != 1100 || msgs[2].Ts != 42000 {
		t.Fatalf("timestamps %d %d", msgs[1].Ts, msgs[2].Ts)
	}
}
