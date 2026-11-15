package agents

import (
	"context"
	"encoding/json"
	"strings"
	"time"

	"sh.simple/simpled/internal/detect"
	"sh.simple/simpled/internal/hc"
)

func SendPromptWhenQuiet(ctx context.Context, client *hc.Client, termID, kind, text string, done func(bool)) {
	prompt := strings.TrimSpace(text)
	if prompt == "" {
		return
	}
	if done == nil {
		done = func(bool) {}
	}
	ctx = context.WithoutCancel(ctx)
	go func() {
		var last string
		stable := 0
		tick := time.NewTicker(300 * time.Millisecond)
		defer tick.Stop()
		deadline := time.After(10 * time.Minute)
		finish := func(sent bool) {
			done(sent)
		}
		for {
			select {
			case <-ctx.Done():
				finish(false)
				return
			case <-deadline:
				finish(false)
				return
			case <-tick.C:
				var rr struct {
					Text string `json:"text"`
				}
				if client.Call(ctx, "terminal.read", map[string]any{"terminal": termID}, &rr) != nil {
					continue
				}
				if _, ok := ClassifyIdle(rr.Text, prompt); ok {
					finish(false)
					return
				}
				if paneReady(kind, rr.Text) && rr.Text == last {
					stable++
				} else {
					stable = 0
				}
				last = rr.Text
				if stable >= 2 {
					sentMs := uint64(time.Now().UnixMilli())
					var out json.RawMessage
					err := client.Call(ctx, "terminal.send", map[string]any{
						"terminal": termID,
						"text":     prompt,
					}, &out)
					finish(err == nil)
					if err != nil {
						return
					}
					if kind == "claude" {
						submitIfStuck(ctx, client, termID)
					}
					submitUntilAccepted(ctx, client, termID, sentMs, acceptPoll, acceptTries)
					return
				}
			}
		}
	}()
}

func paneReady(kind, text string) bool {
	if strings.TrimSpace(text) == "" {
		return false
	}
	if detect.ParseForKind(kind, text) != nil {
		return false
	}
	if kind == "shell" {
		lines := strings.Split(strings.TrimRight(text, "\n"), "\n")
		if len(lines) == 0 {
			return false
		}
		last := strings.TrimSpace(lines[len(lines)-1])
		return strings.HasSuffix(last, "$") || strings.HasSuffix(last, "#")
	}
	return true
}

const (
	acceptPoll  = 1500 * time.Millisecond
	acceptTries = 3
)

func promptAccepted(t hc.Terminal, sentMs uint64) bool {
	if t.Status != nil && t.Status.State == "working" {
		return true
	}
	return t.StatusMs != nil && *t.StatusMs > sentMs
}

func findTerminal(ctx context.Context, client *hc.Client, termID string) (hc.Terminal, bool) {
	list, err := hc.ReadAll(ctx, client)
	if err != nil {
		return hc.Terminal{}, false
	}
	for _, t := range list {
		if t.Address == termID {
			return t, true
		}
	}
	return hc.Terminal{}, false
}

func submitUntilAccepted(ctx context.Context, client *hc.Client, termID string, sentMs uint64, poll time.Duration, tries int) {
	for i := 0; i < tries; i++ {
		time.Sleep(poll)
		t, ok := findTerminal(ctx, client, termID)
		if !ok || t.State != "live" || promptAccepted(t, sentMs) {
			return
		}
		var out json.RawMessage
		_ = client.Call(ctx, "terminal.keys", map[string]any{"terminal": termID, "keys": []string{"enter"}}, &out)
	}
}

const (
	stuckPoll   = 500 * time.Millisecond
	stuckWindow = 8 * time.Second
	stuckReads  = 4
)

func submitIfStuck(ctx context.Context, client *hc.Client, termID string) {
	deadline := time.Now().Add(stuckWindow)
	var last string
	same := 0
	for time.Now().Before(deadline) {
		time.Sleep(stuckPoll)
		var rr struct {
			Text string `json:"text"`
		}
		if client.Call(ctx, "terminal.read", map[string]any{"terminal": termID}, &rr) != nil {
			continue
		}
		line, ok := pendingClaudeInput(rr.Text)
		if !ok {
			return
		}
		if line == last {
			same++
		} else {
			same = 0
		}
		last = line
		if same >= stuckReads {
			var out json.RawMessage
			_ = client.Call(ctx, "terminal.keys", map[string]any{"terminal": termID, "keys": []string{"enter"}}, &out)
			return
		}
	}
}

func pendingClaudeInput(screen string) (string, bool) {
	lines := strings.Split(screen, "\n")
	for i := len(lines) - 1; i >= 0; i-- {
		line := strings.TrimSpace(lines[i])
		rest, ok := strings.CutPrefix(line, "❯")
		if !ok {
			continue
		}
		rest = strings.TrimSpace(rest)
		return rest, rest != ""
	}
	return "", false
}
