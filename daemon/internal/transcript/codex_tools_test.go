package transcript_test

import (
	"path/filepath"
	"runtime"
	"strings"
	"testing"

	"sh.simple/simpled/internal/transcript"
	"sh.simple/simpled/internal/wire"
)

func codexToolByID(t *testing.T, msgs []wire.Message, id string) *wire.Tool {
	t.Helper()
	for _, m := range msgs {
		if m.ID == id && m.Tool != nil {
			return m.Tool
		}
	}
	t.Fatalf("tool %s missing", id)
	return nil
}

func codexUserTexts(msgs []wire.Message) []string {
	var out []string
	for _, m := range msgs {
		if m.Role == "user" && m.Text != nil {
			out = append(out, *m.Text)
		}
	}
	return out
}

func TestCodexToolsFixture(t *testing.T) {
	_, file, _, ok := runtime.Caller(0)
	if !ok {
		t.Fatal("caller")
	}
	path := filepath.Join(filepath.Dir(file), "testdata", "codex_tools.jsonl")
	msgs, err := transcript.ParseCodexJSONL(path)
	if err != nil {
		t.Fatal(err)
	}
	okShell := codexToolByID(t, msgs, "shell-ok")
	if okShell.Name != "exec_command" || okShell.Status != "ok" || okShell.Summary != "go test ./pkg" {
		t.Fatalf("ok shell %+v", okShell)
	}
	if okShell.Input == nil || !strings.Contains(*okShell.Input, "go test ./pkg") {
		t.Fatalf("ok shell input %v", okShell.Input)
	}
	failShell := codexToolByID(t, msgs, "shell-fail")
	if failShell.Status != "error" || failShell.Output == nil || !strings.Contains(*failShell.Output, "FAIL pkg") {
		t.Fatalf("fail shell %+v", failShell)
	}
	patch := codexToolByID(t, msgs, "patch-1")
	if patch.Summary != "src/main.rs" || patch.Diff == nil {
		t.Fatalf("patch %+v", patch)
	}
	wantDiff := "-old line\n+new line"
	if *patch.Diff != wantDiff {
		t.Fatalf("patch diff %q", *patch.Diff)
	}
	plan := codexToolByID(t, msgs, "plan-1")
	if plan.Summary != "1 of 2 done" || plan.Input == nil || !strings.Contains(*plan.Input, "plan") {
		t.Fatalf("plan %+v", plan)
	}
	for _, text := range codexUserTexts(msgs) {
		if strings.HasPrefix(text, "<environment_context>") {
			t.Fatalf("leaked context %q", text)
		}
	}
	var shipCount int
	for _, m := range msgs {
		if m.Role == "user" && m.Text != nil && *m.Text == "Ship it today" {
			shipCount++
		}
	}
	if shipCount != 1 {
		t.Fatalf("duplicate user ship count %d", shipCount)
	}
	abortShell := codexToolByID(t, msgs, "shell-abort")
	if abortShell.Status != "error" {
		t.Fatalf("abort shell %+v", abortShell)
	}
	var stopped bool
	for _, m := range msgs {
		if m.Role == "assistant" && m.Text != nil && *m.Text == "Codex stopped: interrupted" {
			stopped = true
		}
	}
	if !stopped {
		t.Fatal("missing Codex stopped line")
	}
}

func TestCodexReasoningSummaryIsThinking(t *testing.T) {
	msgs, err := transcript.ParseCodexJSONL("testdata/codex_tools.jsonl")
	if err != nil {
		t.Fatal(err)
	}
	last := msgs[len(msgs)-1]
	if last.Role != "thinking" || *last.Text != "**Checking tests**" {
		t.Fatalf("last %+v", last)
	}
}
