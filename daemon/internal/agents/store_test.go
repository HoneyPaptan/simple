package agents_test

import (
	"encoding/json"
	"testing"

	"sh.simple/simpled/internal/agents"
	"sh.simple/simpled/internal/hc"
)

const sampleTerminal = `{
  "address": "t9",
  "argv": ["claude"],
  "cwd": "/home/dev/repo/pkg",
  "created_ms": 1000,
  "labels": {"agent": "claude"},
  "state": "live",
  "status": {"state": "blocked", "kind": "question", "msg": "Continue?", "title": "Plan"}
}`

func TestDetectKindFromSimpleKindLabel(t *testing.T) {
	term := hc.Terminal{
		Argv:   []string{"/bin/bash", "-l"},
		Labels: map[string]string{"simple.kind": "shell"},
	}
	if agents.DetectKind(term) != "shell" {
		t.Fatalf("kind %s", agents.DetectKind(term))
	}
}

func TestShellAgentDefaultTitleIsKind(t *testing.T) {
	s := agents.NewStore(func(string, any) {})
	term := hc.Terminal{
		Address:   "t1",
		Argv:      []string{"/bin/bash", "-l"},
		Cwd:       "/home/dev/demo",
		CreatedMs: 1000,
		State:     "live",
		Labels:    map[string]string{"simple.kind": "shell"},
		Status:    &hc.StatusRecord{Title: "/bin/bash -l"},
	}
	s.LoadSnapshot([]hc.Terminal{term})
	list := s.Snapshot()
	if len(list) != 1 {
		t.Fatalf("agents %d", len(list))
	}
	if list[0].Kind != "shell" {
		t.Fatalf("kind %s", list[0].Kind)
	}
	if list[0].Title != "Shell" {
		t.Fatalf("title %q", list[0].Title)
	}
}

func TestPromptAndFolderTitlesBecomeKind(t *testing.T) {
	cases := map[string]string{
		"simple@simple:~/projects/demo": "Shell",
		"demo":                          "Shell",
		"Fix the header":                "Fix the header",
	}
	for in, want := range cases {
		s := agents.NewStore(func(string, any) {})
		s.LoadSnapshot([]hc.Terminal{{
			Address: "t1",
			Argv:    []string{"bash"},
			Cwd:     "/home/dev/demo",
			State:   "live",
			Labels:  map[string]string{"simple.kind": "shell"},
			Title:   in,
		}})
		if got := s.Snapshot()[0].Title; got != want {
			t.Fatalf("%q: title %q want %q", in, got, want)
		}
	}
}

func TestKindAndStatusFromJSON(t *testing.T) {
	var term hc.Terminal
	if err := json.Unmarshal([]byte(sampleTerminal), &term); err != nil {
		t.Fatal(err)
	}
	if agents.DetectKind(term) != "claude" {
		t.Fatalf("kind %s", agents.DetectKind(term))
	}
	st, w := agents.MapStatus(term)
	if st != "waiting" || w == nil {
		t.Fatalf("status %s", st)
	}
}

func TestStorePublishesOnChange(t *testing.T) {
	var updates int
	s := agents.NewStore(func(ev string, _ any) {
		if ev == "agent.update" {
			updates++
		}
	})
	var term hc.Terminal
	_ = json.Unmarshal([]byte(sampleTerminal), &term)
	s.LoadSnapshot([]hc.Terminal{term})
	n1 := updates
	s.LoadSnapshot([]hc.Terminal{term})
	if updates != n1 {
		t.Fatalf("dup updates %d", updates)
	}
	s.HandleEvent(hc.Event{Seq: 2, Terminal: "t9", Type: "updated", Data: json.RawMessage(`{"status":{"state":"blocked","kind":"question","msg":"Continue?","title":"New title"}}`)})
	if updates < 2 {
		t.Fatalf("updates %d", updates)
	}
}

func TestWorkspaceUpdateEvents(t *testing.T) {
	var wsEvents []string
	s := agents.NewStore(func(ev string, data any) {
		if ev != "workspace.update" {
			return
		}
		b, _ := json.Marshal(data)
		wsEvents = append(wsEvents, string(b))
	})
	term1 := hc.Terminal{
		Address:   "t1",
		Argv:      []string{"bash"},
		Cwd:       "/home/dev/repo",
		CreatedMs: 1000,
		State:     "live",
	}
	term2 := hc.Terminal{
		Address:   "t2",
		Argv:      []string{"bash"},
		Cwd:       "/home/dev/repo",
		CreatedMs: 1000,
		State:     "live",
	}
	term3 := hc.Terminal{
		Address:   "t3",
		Argv:      []string{"bash"},
		Cwd:       "/home/other/proj",
		CreatedMs: 1000,
		State:     "live",
	}
	s.LoadSnapshot([]hc.Terminal{term1})
	if len(wsEvents) != 1 {
		t.Fatalf("first ws events %d", len(wsEvents))
	}
	s.LoadSnapshot([]hc.Terminal{term1, term2})
	if len(wsEvents) != 2 {
		t.Fatalf("count bump events %d", len(wsEvents))
	}
	var last map[string]any
	_ = json.Unmarshal([]byte(wsEvents[len(wsEvents)-1]), &last)
	if int(last["agents"].(float64)) != 2 {
		t.Fatalf("agents %v", last["agents"])
	}
	s.LoadSnapshot([]hc.Terminal{term1, term2, term3})
	if len(wsEvents) != 3 {
		t.Fatalf("new workspace events %d", len(wsEvents))
	}
	s.HandleEvent(hc.Event{Seq: 1, Terminal: "t3", Type: "removed"})
	if len(wsEvents) != 4 {
		t.Fatalf("remove events %d", len(wsEvents))
	}
}

func TestLiveBlockedAgentWaitsInsteadOfFailing(t *testing.T) {
	status, msg := agents.MapStatus(hc.Terminal{
		State:  "live",
		Status: &hc.StatusRecord{State: "blocked", Msg: "dialog open"},
	})
	if status != "waiting" || msg != nil {
		t.Fatalf("status %s msg %v", status, msg)
	}
}
