package transcript

import "testing"

func TestClaudeThinkingAndStopRows(t *testing.T) {
	msgs, err := ParseClaudeJSONL("testdata/thinking.jsonl")
	if err != nil {
		t.Fatal(err)
	}
	var roles []string
	for _, m := range msgs {
		roles = append(roles, m.Role)
	}
	want := []string{"user", "thinking", "assistant", "event"}
	if len(roles) != len(want) {
		t.Fatalf("roles %v", roles)
	}
	for i := range want {
		if roles[i] != want[i] {
			t.Fatalf("roles %v", roles)
		}
	}
	if *msgs[1].Text != "Look at main.go first." || *msgs[3].Text != "Stopped" {
		t.Fatalf("texts %q %q", *msgs[1].Text, *msgs[3].Text)
	}
}
