package opencode

import (
	"context"
	"encoding/json"
	"testing"

	"sh.simple/simpled/internal/wire"
)

type fakeTurns struct {
	active map[string]json.RawMessage
	items  []Item
}

func (f *fakeTurns) ActiveSessions(context.Context) (map[string]json.RawMessage, error) {
	return f.active, nil
}

func (f *fakeTurns) Messages(context.Context, string) ([]Item, error) { return f.items, nil }

func TestTurnsFinishAddsDoneItem(t *testing.T) {
	api := &fakeTurns{active: map[string]json.RawMessage{}}
	box := &fakeInbox{}
	var statuses []string
	ag := []wire.Agent{agentWith("t1", "ses_a", "ask")}
	tr := &Turns{API: api, Inbox: box, Agents: func() []wire.Agent { return ag }, SetStatus: func(id, s string) { statuses = append(statuses, id+"="+s) }}
	ctx := context.Background()
	tr.Poll(ctx)
	if len(box.added) != 0 || len(statuses) != 0 {
		t.Fatalf("idle start produced %v %v", box.added, statuses)
	}
	api.active["ses_a"] = json.RawMessage(`{}`)
	tr.Poll(ctx)
	api.items = []Item{{ID: "m", Type: "assistant", Content: []Block{{Type: "text", Text: "All done."}}}}
	delete(api.active, "ses_a")
	tr.Poll(ctx)
	if len(statuses) != 2 || statuses[0] != "t1=working" || statuses[1] != "t1=done" {
		t.Fatalf("statuses %v", statuses)
	}
	if len(box.added) != 1 || box.added[0].Kind != "done" || *box.added[0].Body != "All done." {
		t.Fatalf("added %#v", box.added)
	}
}

func TestTurnsFinishSkipsDoneWhenSendFailed(t *testing.T) {
	api := &fakeTurns{active: map[string]json.RawMessage{"ses_a": json.RawMessage(`{}`)}}
	box := &fakeInbox{}
	var statuses []string
	ag := []wire.Agent{agentWith("t1", "ses_a", "ask")}
	api.items = []Item{
		{ID: "u", Type: "user", Text: "probe"},
		{ID: "f", Type: "idle", Outcome: "failed", Error: []byte(`{"message":"invalid api key"}`)},
	}
	tr := &Turns{
		API: api, Inbox: box, Agents: func() []wire.Agent { return ag },
		SetStatus: func(id, s string) { statuses = append(statuses, id+"="+s) },
		Observe:   func(string, []Item) bool { return true },
	}
	ctx := context.Background()
	tr.Poll(ctx)
	delete(api.active, "ses_a")
	tr.Poll(ctx)
	if len(box.added) != 0 {
		t.Fatalf("inbox %#v", box.added)
	}
	for _, st := range statuses {
		if st == "t1=done" {
			t.Fatalf("done overwrote failure: %v", statuses)
		}
	}
}

func TestDoneTitleMarksStoppedTurns(t *testing.T) {
	if doneTitle(stoppedPrefix+"Free usage limit reached.") != "Stopped" || doneTitle("All set.") != "Finished" {
		t.Fatal("wrong done title")
	}
}
