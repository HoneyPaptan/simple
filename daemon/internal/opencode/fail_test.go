package opencode

import "testing"

func TestFailedTurnUsesOpenCodeMessage(t *testing.T) {
	items := []Item{
		{ID: "u", Type: "user", Text: "probe", Time: Times{Created: 10}},
		{ID: "f", Type: "idle", Outcome: "failed", Error: []byte(`{"message":"invalid api key"}`), Time: Times{Created: 20}},
	}
	id, text, ts, ok := FailedTurn(items)
	if !ok || id != "f" || text != "invalid api key" || ts != 20 {
		t.Fatalf("turn %q %q %d %v", id, text, ts, ok)
	}
	msgs := OmitFailureEcho(ToWire(items), id, text)
	for _, m := range msgs {
		if m.Role == "assistant" {
			t.Fatalf("echo still present: %+v", m)
		}
	}
}

func TestFailedTurnIgnoresEarlierFailure(t *testing.T) {
	items := []Item{
		{ID: "u1", Type: "user", Text: "old", Time: Times{Created: 1}},
		{ID: "old", Type: "idle", Outcome: "failed", Error: []byte(`"old key"`), Time: Times{Created: 2}},
		{ID: "u2", Type: "user", Text: "next", Time: Times{Created: 3}},
		{ID: "a", Type: "assistant", Time: Times{Created: 4, Completed: 5}, Content: []Block{{Type: "text", Text: "ok"}}},
		{ID: "done", Type: "idle", Outcome: "succeeded", Time: Times{Created: 6}},
	}
	if _, _, _, ok := FailedTurn(items); ok {
		t.Fatal("settled turn reported failed")
	}
	if ids := FailedIDs(items); len(ids) != 1 || ids[0] != "old" {
		t.Fatalf("ids %v", ids)
	}
}

func TestMessageFromEventReadsNestedProviderError(t *testing.T) {
	root := map[string]any{
		"type": "session.error",
		"properties": map[string]any{
			"sessionID": "ses_1",
			"error": map[string]any{
				"name": "APIError",
				"data": map[string]any{"message": "invalid api key"},
			},
		},
	}
	if got := MessageFromEvent(root); got != "invalid api key" {
		t.Fatalf("event message %q", got)
	}
}
