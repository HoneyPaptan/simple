package agents

import (
	"context"
	"strings"
	"testing"
	"time"

	"sh.simple/simpled/internal/hc"
)

func TestClassifyIdleSkipsPromptEcho(t *testing.T) {
	prompt := "why command not found"
	if _, ok := ClassifyIdle(prompt, prompt); ok {
		t.Fatal("prompt echo matched")
	}
	line, ok := ClassifyIdle("bash: claude: command not found\n", prompt)
	if !ok || line != "bash: claude: command not found" {
		t.Fatalf("%q %v", line, ok)
	}
}

func TestClassifyIdleSkipsPromptInsideTheBox(t *testing.T) {
	prompts := []string{
		"fix the unauthorized error in login",
		"why does it say no such file",
	}
	for _, prompt := range prompts {
		if _, ok := ClassifyIdle(prompt, prompt); ok {
			t.Fatalf("plain %q", prompt)
		}
		if _, ok := ClassifyIdle("> "+prompt, prompt); ok {
			t.Fatalf("prefixed %q", prompt)
		}
		mid := len(prompt) / 2
		wrapped := "│ " + prompt[:mid] + " │\n│ " + prompt[mid:] + " │"
		if _, ok := ClassifyIdle(wrapped, prompt); ok {
			t.Fatalf("wrapped %q", prompt)
		}
	}
	line, ok := ClassifyIdle("Not logged in. Run /login", prompts[0])
	if !ok || line != "Not logged in. Run /login" {
		t.Fatalf("auth %q %v", line, ok)
	}
}

func TestSendWatchIgnoresCommandNotFoundOnceTurnStarts(t *testing.T) {
	failed := false
	RunSendWatch(context.Background(), SendWatch{
		Kind:     "claude",
		Wait:     40 * time.Millisecond,
		Poll:     5 * time.Millisecond,
		ClearFor: 20 * time.Millisecond,
		Classify: true,
		Read: func(context.Context) string {
			return "bash: tool: command not found"
		},
		TurnStarted: func() bool { return true },
		Fail:        func(string) { failed = true },
	})
	if failed {
		t.Fatal("running turn was marked failed")
	}
}

func TestSendWatchRechecksTurnBeforeMatchingCommandNotFound(t *testing.T) {
	failed := false
	started := false
	RunSendWatch(context.Background(), SendWatch{
		Kind:     "claude",
		Wait:     40 * time.Millisecond,
		Poll:     5 * time.Millisecond,
		ClearFor: 20 * time.Millisecond,
		Classify: true,
		Read: func(context.Context) string {
			started = true
			return "bash: tool: command not found"
		},
		TurnStarted: func() bool { return started },
		Fail:        func(string) { failed = true },
	})
	if failed {
		t.Fatal("command not found inside a starting turn was marked failed")
	}
}

func TestSendWatchFailsIdleAuthAndClearsWhenPromptLands(t *testing.T) {
	var got string
	cleared := false
	landed := false
	RunSendWatch(context.Background(), SendWatch{
		Kind:     "claude",
		Wait:     200 * time.Millisecond,
		Poll:     5 * time.Millisecond,
		ClearFor: time.Second,
		Classify: true,
		Read: func(context.Context) string {
			return "Not logged in. Run /login"
		},
		TurnStarted: func() bool { return false },
		PromptLanded: func() bool {
			return landed
		},
		Fail: func(text string) {
			got = text
			landed = true
		},
		Clear: func() { cleared = true },
	})
	if got != "Not logged in. Run /login" {
		t.Fatalf("fail %q", got)
	}
	if !cleared {
		t.Fatal("latched failure was not cleared")
	}
}

func TestSendWatchTimesOutWhenTurnNeverStarts(t *testing.T) {
	var got string
	RunSendWatch(context.Background(), SendWatch{
		Kind:        "opencode",
		Wait:        30 * time.Millisecond,
		Poll:        5 * time.Millisecond,
		ClearFor:    15 * time.Millisecond,
		Classify:    true,
		Read:        func(context.Context) string { return "ready" },
		TurnStarted: func() bool { return false },
		Fail:        func(text string) { got = text },
	})
	if got != "OpenCode did not start." {
		t.Fatalf("fail %q", got)
	}
}

func TestLatchSurvivesIdlePollAndClearsOnWorking(t *testing.T) {
	var events []string
	s := NewStore(func(ev string, _ any) {
		events = append(events, ev)
	})
	idle := hc.Terminal{
		Address: "t1",
		State:   "live",
		Cwd:     "/tmp/demo",
		Argv:    []string{"claude"},
		Status:  &hc.StatusRecord{State: "idle"},
	}
	s.LoadSnapshot([]hc.Terminal{idle})
	s.LatchSendFailure("t1", "Not logged in")
	s.LoadSnapshot([]hc.Terminal{idle})
	a, ok := s.Get("t1")
	if !ok || a.Status != "failed" {
		t.Fatalf("status %+v", a)
	}
	ev, ok := s.SendEvent("t1")
	if !ok || ev.Text == nil || *ev.Text != "Not logged in" {
		t.Fatalf("event %+v", ev)
	}
	if ids := s.PollableAgentIDs(); !containsID(ids, "t1") {
		t.Fatalf("send latch not polled: %v", ids)
	}
	working := idle
	working.Status = &hc.StatusRecord{State: "working"}
	s.LoadSnapshot([]hc.Terminal{working})
	a, _ = s.Get("t1")
	if a.Status != "working" {
		t.Fatalf("working status %s", a.Status)
	}
	if _, ok := s.SendEvent("t1"); ok {
		t.Fatal("event still latched")
	}
	code := int32(1)
	s.LoadSnapshot([]hc.Terminal{{
		Address:  "dead",
		State:    "finished",
		ExitCode: &code,
		Cwd:      "/tmp/demo",
		Argv:     []string{"claude"},
	}})
	for _, id := range s.PollableAgentIDs() {
		if id == "dead" {
			t.Fatal("exited agent is polled")
		}
	}
	joined := strings.Join(events, ",")
	if !strings.Contains(joined, "agent.update") || !strings.Contains(joined, "transcript.append") {
		t.Fatalf("events %s", joined)
	}
}

func containsID(ids []string, want string) bool {
	for _, id := range ids {
		if id == want {
			return true
		}
	}
	return false
}
