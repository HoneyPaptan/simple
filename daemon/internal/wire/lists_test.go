package wire

import (
	"encoding/json"
	"testing"
)

func TestEmptyListsEncodeAsArrays(t *testing.T) {
	cases := map[string]any{
		`{"agents":[],"inbox":[],"workspaces":[]}`: Snapshot{},
		`{"agents":[]}`:     AgentsListResult{},
		`{"workspaces":[]}`: WorkspacesListResult{},
		`{"items":[]}`:      InboxListResult{},
		`{"entries":[]}`:    FilesListResult{},
		`{"branch":"","ahead":0,"behind":0,"files":[]}`: GitStatusResult{},
		`{"snapshot":{"agents":[],"inbox":[],"workspaces":[]}}`: struct {
			Snapshot *Snapshot `json:"snapshot"`
		}{&Snapshot{}},
	}
	for want, v := range cases {
		got, err := json.Marshal(v)
		if err != nil {
			t.Fatal(err)
		}
		if string(got) != want {
			t.Errorf("got %s, want %s", got, want)
		}
	}
}

func TestGitStatusMarshalKeepsEveryField(t *testing.T) {
	b, err := json.Marshal(GitStatusResult{Branch: "master", Ahead: 2, Behind: 1, Upstream: true})
	if err != nil {
		t.Fatal(err)
	}
	want := `{"branch":"master","ahead":2,"behind":1,"upstream":true,"files":[]}`
	if string(b) != want {
		t.Fatalf("got %s want %s", b, want)
	}
}

func TestNilListsMarshalAsEmptyArrays(t *testing.T) {
	for _, v := range []any{Snapshot{}, AgentsListResult{}, WorkspacesListResult{}, InboxListResult{}, FilesListResult{}} {
		b, err := json.Marshal(v)
		if err != nil {
			t.Fatal(err)
		}
		var m map[string]json.RawMessage
		if err := json.Unmarshal(b, &m); err != nil {
			t.Fatal(err)
		}
		for k, raw := range m {
			if string(raw) == "null" {
				t.Fatalf("%T field %s is null", v, k)
			}
		}
	}
}
