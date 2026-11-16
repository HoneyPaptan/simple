package agents

import (
	"context"
	"strings"
	"testing"
	"time"

	"sh.simple/simpled/internal/hc"
	"sh.simple/simpled/internal/opencode"
	"sh.simple/simpled/internal/wire"
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

const claudeThemeScreen = "Welcome to Claude Code v2.1.288\n\n Let's get started.\n\n Choose the text style that looks best with your terminal\n To change this later, run /theme\n\n     Auto (match terminal)\n ❯ ✔ Dark mode\n     Light mode\n     Dark mode (colorblind-friendly)\n     Light mode (colorblind-friendly)\n     Dark mode (ANSI colors only)\n     Light mode (ANSI colors only)\n\n 1  function greet() {\n 2 -  console.log(\"Hello, World!\");\n 2 +  console.log(\"Hello, Claude!\");\n 3  }\n Syntax theme: Monokai Extended (ctrl+t to disable)\n"

const claudeLoginMethodScreen = "Welcome to Claude Code v2.1.288\n\n Claude Code can be used with your Claude subscription or billed based on API\n usage through your Console account.\n\n Select login method:\n\n ❯ 1. Claude account with subscription\n   2. Anthropic Console account\n   3. 3rd-party platform\n"

const claudeLoggedOutScreen = "Welcome to Claude Code v2.1.288\n\n Browser didn't open? Use the url below to sign in (c to copy)\n\n https://claude.com/cai/oauth/authorize?code=redacted\n\n Paste code here if prompted >\n"

func TestClassifyIdleMatchesClaudeLoggedOutScreen(t *testing.T) {
	prompt := "what should we build"
	line, ok := ClassifyIdle(claudeThemeScreen, prompt)
	if !ok || line != "Choose the text style that looks best with your terminal" {
		t.Fatalf("theme %q %v", line, ok)
	}
	line, ok = ClassifyIdle(claudeLoginMethodScreen, prompt)
	if !ok || line != "Select login method:" {
		t.Fatalf("method %q %v", line, ok)
	}
	line, ok = ClassifyIdle(claudeLoggedOutScreen, prompt)
	if !ok || line != "Paste code here if prompted >" {
		t.Fatalf("paste %q %v", line, ok)
	}
	line, ok = ClassifyIdle("OAuth error: Invalid code. Please make sure the full code was copied\n", prompt)
	if !ok || !strings.Contains(line, "OAuth error") {
		t.Fatalf("oauth line %q %v", line, ok)
	}
	if _, ok := ClassifyIdle(claudeLoggedOutScreen, "Paste code here if prompted >"); !ok {
		t.Fatal("overlap hid the other login line")
	}
}

func TestSendWatchFailsClaudeLoginBeforeTimeout(t *testing.T) {
	var got string
	var failedAt time.Time
	start := time.Now()
	RunSendWatch(context.Background(), SendWatch{
		Kind:        "claude",
		Prompt:      "what should we build",
		Wait:        time.Second,
		Poll:        20 * time.Millisecond,
		ClearFor:    30 * time.Millisecond,
		Classify:    true,
		Read:        func(context.Context) string { return claudeThemeScreen },
		TurnStarted: func() bool { return false },
		Fail: func(text string) {
			if failedAt.IsZero() {
				got = text
				failedAt = time.Now()
			}
		},
	})
	if got != "Claude is not signed in. Open its terminal to sign in." {
		t.Fatalf("fail %q", got)
	}
	if failedAt.IsZero() || failedAt.Sub(start) > 300*time.Millisecond {
		t.Fatalf("login screen waited %s", failedAt.Sub(start))
	}
}

func TestClassifyIdleIgnoresUnauthorizedAboveTheLastLines(t *testing.T) {
	var b strings.Builder
	b.WriteString("401 Unauthorized\n")
	for i := 0; i < 30; i++ {
		b.WriteString("idle output\n")
	}
	prompt := "what should we build"
	if _, ok := ClassifyIdle(b.String(), prompt); ok {
		t.Fatal("unauthorized 30 lines up latched")
	}
	if _, ok := ClassifyIdle("401 Unauthorized\n", prompt); !ok {
		t.Fatal("recent unauthorized was ignored")
	}
}

func TestSignInReasonMapsLoginScreensAndKeepsOtherLines(t *testing.T) {
	want := "Claude is not signed in. Open its terminal to sign in."
	for _, line := range []string{
		"Choose the text style that looks best with your terminal",
		"Select login method:",
		"Browser didn't open? Use the url below to sign in (c to copy)",
		"Paste code here if prompted >",
	} {
		if got := SignInReason("claude", line); got != want {
			t.Fatalf("%q -> %q", line, got)
		}
	}
	if got := SignInReason("opencode", "Paste code here if prompted >"); got != "OpenCode is not signed in. Open its terminal to sign in." {
		t.Fatalf("kind %q", got)
	}
	raw := "invalid api key"
	if got := SignInReason("claude", raw); got != raw {
		t.Fatalf("api key %q", got)
	}
	raw = "Not logged in. Run /login"
	if got := SignInReason("claude", raw); got != raw {
		t.Fatalf("login line %q", got)
	}
	raw = "failed to run hook"
	if got := SignInReason("claude", raw); got != raw {
		t.Fatalf("hook %q", got)
	}
}

func TestHoldLatchedAuthKeepsLoginTextAndAllowsANormalPrompt(t *testing.T) {
	reason, hold := HoldLatchedAuth("Paste code here if prompted >", claudeLoggedOutScreen, "simp6 follow up")
	if !hold || reason != "Paste code here if prompted >" {
		t.Fatalf("hold %q %v", reason, hold)
	}
	normal := "❯ what should we build\n"
	if _, hold := HoldLatchedAuth("Paste code here if prompted >", normal, "retry the prompt"); hold {
		t.Fatal("normal prompt was held")
	}
	if _, hold := HoldLatchedAuth("", claudeLoggedOutScreen, "simp6 follow up"); hold {
		t.Fatal("unlatched screen was held")
	}
}

func TestOpenCodeFailureLatchesAfterSend(t *testing.T) {
	s := NewStore(func(string, any) {})
	s.Upsert(wire.Agent{ID: "t1", Kind: "opencode", Status: "idle"})
	old := []opencode.Item{{ID: "old", Type: "idle", Outcome: "failed", Error: []byte(`"stale key"`)}}
	s.NoteOpenCodeSend("t1", opencode.FailedIDs(old), time.Now().UnixMilli())
	if s.ObserveOpenCodeTurn("t1", old) {
		t.Fatal("baseline failure latched")
	}
	now := time.Now().UnixMilli()
	items := []opencode.Item{
		{ID: "u", Type: "user", Text: "probe", Time: opencode.Times{Created: now}},
		{ID: "f", Type: "idle", Outcome: "failed", Error: []byte(`{"message":"invalid api key"}`), Time: opencode.Times{Created: now}},
	}
	if !s.ObserveOpenCodeTurn("t1", items) {
		t.Fatal("new failure not latched")
	}
	a, ok := s.Get("t1")
	if !ok || a.Status != "failed" {
		t.Fatalf("status %+v", a)
	}
	ev, ok := s.SendEvent("t1")
	if !ok || ev.ID != "sf:t1" || ev.Text == nil || *ev.Text != "invalid api key" {
		t.Fatalf("event %+v", ev)
	}
	s.SetAgentFields("t1", "done", nil)
	a, _ = s.Get("t1")
	if a.Status != "failed" {
		t.Fatalf("done clobbered %s", a.Status)
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
