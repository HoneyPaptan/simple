package opencode

import (
	"context"
	"slices"
	"sync"
	"testing"

	"sh.simple/simpled/internal/wire"
)

type fakeAPI struct {
	mu        sync.Mutex
	open      map[string][]Permission
	replies   []string
	rules     map[string][]Rule
	ruleCalls int
}

func (f *fakeAPI) Permissions(_ context.Context, session string) ([]Permission, error) {
	f.mu.Lock()
	defer f.mu.Unlock()
	return append([]Permission(nil), f.open[session]...), nil
}

func (f *fakeAPI) ReplyPermission(_ context.Context, session, request, decision string) error {
	f.mu.Lock()
	defer f.mu.Unlock()
	f.replies = append(f.replies, session+"/"+request+"="+decision)
	var keep []Permission
	for _, p := range f.open[session] {
		if p.ID != request {
			keep = append(keep, p)
		}
	}
	f.open[session] = keep
	return nil
}

func (f *fakeAPI) SetRules(_ context.Context, session string, rules []Rule) error {
	f.mu.Lock()
	defer f.mu.Unlock()
	if f.rules == nil {
		f.rules = map[string][]Rule{}
	}
	f.rules[session] = rules
	f.ruleCalls++
	return nil
}

type fakeInbox struct {
	added    []wire.Item
	resolved []string
}

func (f *fakeInbox) Add(item wire.Item) error {
	f.added = append(f.added, item)
	return nil
}

func (f *fakeInbox) Resolve(id string) { f.resolved = append(f.resolved, id) }

func agentWith(id, session, mode string) wire.Agent {
	return wire.Agent{ID: id, Kind: "opencode", Session: &session, Mode: &mode}
}

func TestApprovalsCardAnswerAndReply(t *testing.T) {
	api := &fakeAPI{open: map[string][]Permission{"ses_a": {{ID: "per_1", Action: "bash", Resources: []string{"git push"}}}}}
	box := &fakeInbox{}
	ag := []wire.Agent{agentWith("t1", "ses_a", "ask")}
	a := &Approvals{API: api, Inbox: box, Agents: func() []wire.Agent { return ag }}
	ctx := context.Background()
	a.Poll(ctx)
	a.Poll(ctx)
	if len(box.added) != 1 {
		t.Fatalf("added %d", len(box.added))
	}
	item := box.added[0]
	if item.Title != "Run git push" || item.Agent != "t1" || item.Kind != "approval" {
		t.Fatalf("item %#v", item)
	}
	if !a.Answer(ctx, item.ID, "always") {
		t.Fatal("answer not handled")
	}
	if len(api.replies) != 1 || api.replies[0] != "ses_a/per_1=always" {
		t.Fatalf("replies %v", api.replies)
	}
	if a.Answer(ctx, "other", "allow") {
		t.Fatal("unknown item handled")
	}
}

func TestApprovalsResolveWhenAnsweredInTerminal(t *testing.T) {
	api := &fakeAPI{open: map[string][]Permission{"ses_a": {{ID: "per_1", Action: "edit", Resources: []string{"main.go"}}}}}
	box := &fakeInbox{}
	a := &Approvals{API: api, Inbox: box, Agents: func() []wire.Agent { return []wire.Agent{agentWith("t1", "ses_a", "ask")} }}
	a.Poll(context.Background())
	api.open["ses_a"] = nil
	a.Poll(context.Background())
	if len(box.resolved) != 1 || box.resolved[0] != box.added[0].ID {
		t.Fatalf("resolved %v", box.resolved)
	}
}

func TestApprovalsModesAutoAllow(t *testing.T) {
	api := &fakeAPI{open: map[string][]Permission{
		"ses_auto": {{ID: "e", Action: "edit"}, {ID: "b", Action: "bash", Resources: []string{"ls"}}},
		"ses_full": {{ID: "x", Action: "bash"}},
	}}
	box := &fakeInbox{}
	ag := []wire.Agent{agentWith("t1", "ses_auto", "auto"), agentWith("t2", "ses_full", "full")}
	a := &Approvals{API: api, Inbox: box, Agents: func() []wire.Agent { return ag }}
	a.Poll(context.Background())
	if len(box.added) != 2 || box.added[0].Title != "Edit files" || box.added[1].Title != "Run ls" {
		t.Fatalf("added %#v", box.added)
	}
	if len(api.replies) != 1 || api.replies[0] != "ses_full/x=once" {
		t.Fatalf("replies %v", api.replies)
	}
}

func TestParseModelKey(t *testing.T) {
	ref, ok := ParseModelKey("openrouter/liquid/lfm-3", "low")
	if !ok || ref.ProviderID != "openrouter" || ref.ID != "liquid/lfm-3" || ref.Variant != "low" {
		t.Fatalf("ref %#v", ref)
	}
	if _, ok := ParseModelKey("default", ""); ok {
		t.Fatal("default parsed")
	}
}

func TestApprovalsApplyModeRulesOnce(t *testing.T) {
	api := &fakeAPI{open: map[string][]Permission{}}
	mode := "ask"
	session := "ses_a"
	ag := wire.Agent{ID: "t1", Kind: "opencode", Session: &session, Mode: &mode}
	a := &Approvals{API: api, Inbox: &fakeInbox{}, Agents: func() []wire.Agent { return []wire.Agent{ag} }}
	a.Poll(context.Background())
	a.Poll(context.Background())
	if api.ruleCalls != 1 || api.rules["ses_a"][0] != (Rule{Action: "*", Resource: "*", Effect: "ask"}) || slices.Contains(api.rules["ses_a"], Rule{Action: "edit", Resource: "*", Effect: "allow"}) {
		t.Fatalf("calls %d rules %v", api.ruleCalls, api.rules["ses_a"])
	}
	mode = "full"
	a.Poll(context.Background())
	if api.ruleCalls != 2 || len(api.rules["ses_a"]) != 1 || api.rules["ses_a"][0].Effect != "allow" {
		t.Fatalf("full rules %v", api.rules["ses_a"])
	}
}
