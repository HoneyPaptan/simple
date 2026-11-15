package agents

import (
	"context"
	"strings"
	"time"
	"unicode/utf8"

	"sh.simple/simpled/internal/wire"
)

const (
	SendStartWait = 30 * time.Second
	sendFailClip  = 160
)

var authFailPhrases = []string{
	"not logged in",
	"please run /login",
	"run /login",
	"login required",
	"invalid api key",
	"unauthorized",
	"authentication failed",
	"hook failed",
	"hooks failed",
	"failed to run hook",
}

var spawnFailPhrases = []string{
	"command not found",
	"no such file",
}

func DidNotStart(kind string) string {
	return KindLabel(kind) + " did not start."
}

func ExitedLine(kind string) string {
	return KindLabel(kind) + " exited."
}

func CouldNotSend() string {
	return "Could not send."
}

func ClassifyIdle(screen, prompt string) (string, bool) {
	if line, ok := matchPhrases(screen, prompt, authFailPhrases); ok {
		return line, true
	}
	return matchPhrases(screen, prompt, spawnFailPhrases)
}

func ClassifyFresh(prev, next, prompt string) (string, bool) {
	if line, ok := matchPhrases(next, prompt, authFailPhrases); ok {
		return line, true
	}
	if next == prev {
		return "", false
	}
	return matchPhrases(next, prompt, spawnFailPhrases)
}

func matchPhrases(screen, prompt string, phrases []string) (string, bool) {
	want := strings.TrimSpace(prompt)
	lines := nonEmptyLines(screen, 12)
	for i := len(lines) - 1; i >= 0; i-- {
		line := lines[i]
		if lineOverlapsPrompt(line, want) {
			continue
		}
		lower := strings.ToLower(line)
		for _, p := range phrases {
			if strings.Contains(lower, p) {
				return clipLine(line), true
			}
		}
	}
	return "", false
}

func lineOverlapsPrompt(line, prompt string) bool {
	want := strings.ToLower(strings.TrimSpace(prompt))
	if want == "" {
		return false
	}
	got := strings.ToLower(stripPromptChrome(line))
	if got == "" {
		return false
	}
	return strings.Contains(want, got) || strings.Contains(got, want)
}

func stripPromptChrome(line string) string {
	var b strings.Builder
	for _, r := range line {
		if r >= 0x2500 && r <= 0x257F {
			continue
		}
		b.WriteRune(r)
	}
	s := strings.TrimSpace(b.String())
	for strings.HasPrefix(s, ">") {
		s = strings.TrimSpace(strings.TrimPrefix(s, ">"))
	}
	return s
}

func nonEmptyLines(text string, max int) []string {
	parts := strings.Split(text, "\n")
	out := make([]string, 0, max)
	for i := len(parts) - 1; i >= 0 && len(out) < max; i-- {
		t := strings.TrimSpace(parts[i])
		if t == "" {
			continue
		}
		out = append([]string{t}, out...)
	}
	return out
}

func clipLine(s string) string {
	s = strings.TrimSpace(s)
	if utf8.RuneCountInString(s) <= sendFailClip {
		return s
	}
	runes := []rune(s)
	return string(runes[:sendFailClip])
}

func PromptLanded(prompt string, lines []string, since int64, lineTs func(int) int64) bool {
	want := strings.TrimSpace(prompt)
	if want == "" {
		return false
	}
	for i, line := range lines {
		if lineTs != nil && lineTs(i) > 0 && lineTs(i) < since {
			continue
		}
		got := strings.TrimSpace(line)
		if got == want || strings.HasPrefix(got, want) {
			return true
		}
	}
	return false
}

type SendWatch struct {
	Kind         string
	Prompt       string
	Wait         time.Duration
	Poll         time.Duration
	ClearFor     time.Duration
	Classify     bool
	Read         func(context.Context) string
	TurnStarted  func() bool
	PromptLanded func() bool
	Fail         func(string)
	Clear        func()
}

func RunSendWatch(ctx context.Context, w SendWatch) {
	wait := w.Wait
	if wait <= 0 {
		wait = SendStartWait
	}
	poll := w.Poll
	if poll <= 0 {
		poll = time.Second
	}
	clearFor := w.ClearFor
	if clearFor <= 0 {
		clearFor = 10 * time.Minute
	}
	deadline := time.Now().Add(wait)
	clearBy := time.Now().Add(clearFor)
	failed := !w.Classify
	var prev string
	seen := false
	tick := time.NewTicker(poll)
	defer tick.Stop()
	for {
		if ctx.Err() != nil {
			return
		}
		if w.TurnStarted != nil && w.TurnStarted() {
			if failed && w.Clear != nil {
				w.Clear()
			}
			return
		}
		if w.PromptLanded != nil && w.PromptLanded() {
			if failed && w.Clear != nil {
				w.Clear()
			}
			return
		}
		if !failed {
			screen := ""
			if w.Read != nil {
				screen = w.Read(ctx)
			}
			if ctx.Err() != nil {
				return
			}
			if w.TurnStarted != nil && w.TurnStarted() {
				return
			}
			if w.PromptLanded != nil && w.PromptLanded() {
				return
			}
			var text string
			var ok bool
			if seen {
				text, ok = ClassifyFresh(prev, screen, w.Prompt)
			} else {
				text, ok = matchPhrases(screen, w.Prompt, authFailPhrases)
				seen = true
			}
			prev = screen
			if ok {
				failed = true
				if w.Fail != nil {
					w.Fail(text)
				}
				clearBy = time.Now().Add(clearFor)
			} else if !time.Now().Before(deadline) {
				failed = true
				if w.Fail != nil {
					w.Fail(DidNotStart(w.Kind))
				}
				clearBy = time.Now().Add(clearFor)
			}
		} else if !time.Now().Before(clearBy) {
			return
		}
		select {
		case <-ctx.Done():
			return
		case <-tick.C:
		}
	}
}

func failMessage(id, text string) wire.Message {
	t := text
	return wire.Message{
		ID:   "sf:" + id,
		Role: "event",
		Ts:   time.Now().UnixMilli(),
		Text: &t,
	}
}

type sendFail struct {
	text     string
	event    wire.Message
	fromSend bool
}

func (s *Store) LatchSendFailure(id, text string) {
	if s == nil || id == "" || strings.TrimSpace(text) == "" {
		return
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	if a, ok := s.byID[id]; ok && a.Status == "working" {
		return
	}
	if cur, ok := s.sendFail[id]; ok && strings.TrimSpace(cur.text) != "" {
		return
	}
	if s.sendFail == nil {
		s.sendFail = make(map[string]sendFail)
	}
	msg := failMessage(id, text)
	s.sendFail[id] = sendFail{text: text, event: msg, fromSend: true}
	if a, ok := s.byID[id]; ok {
		a.Status = "failed"
		a.Waiting = nil
		a.UpdatedMs = time.Now().UnixMilli()
		cp := *a
		s.pub("agent.update", cp)
	}
	s.pub("transcript.append", wire.TranscriptAppend{ID: id, Messages: []wire.Message{msg}})
}

func (s *Store) ClearSendFailure(id string) {
	if s == nil || id == "" {
		return
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	s.dropLatchLocked(id, true)
}

func (s *Store) SendEvent(id string) (wire.Message, bool) {
	if s == nil {
		return wire.Message{}, false
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	cur, ok := s.sendFail[id]
	if !ok || cur.event.Text == nil || strings.TrimSpace(*cur.event.Text) == "" {
		return wire.Message{}, false
	}
	return cur.event, true
}

func (s *Store) holdSendFail(a *wire.Agent) {
	if s == nil || a == nil {
		return
	}
	if a.Status == "working" {
		s.dropLatchLocked(a.ID, false)
		return
	}
	cur, ok := s.sendFail[a.ID]
	if ok && strings.TrimSpace(cur.text) != "" {
		a.Status = "failed"
		a.Waiting = nil
		return
	}
	if a.Status != "failed" {
		return
	}
	text := ExitedLine(a.Kind)
	msg := failMessage(a.ID, text)
	if s.sendFail == nil {
		s.sendFail = make(map[string]sendFail)
	}
	s.sendFail[a.ID] = sendFail{text: text, event: msg}
	s.pub("transcript.append", wire.TranscriptAppend{ID: a.ID, Messages: []wire.Message{msg}})
}

func (s *Store) dropLatchLocked(id string, restore bool) {
	cur, ok := s.sendFail[id]
	if !ok {
		return
	}
	delete(s.sendFail, id)
	if cur.event.ID != "" {
		blank := ""
		cur.event.Text = &blank
		s.pub("transcript.append", wire.TranscriptAppend{ID: id, Messages: []wire.Message{cur.event}})
	}
	if !restore {
		return
	}
	a, ok := s.byID[id]
	if !ok || a.Status != "failed" {
		return
	}
	if t, ok := s.terms[id]; ok {
		st, w := MapStatus(t)
		a.Status = st
		a.Waiting = w
	} else {
		a.Status = "idle"
		a.Waiting = nil
	}
	a.UpdatedMs = time.Now().UnixMilli()
	cp := *a
	s.pub("agent.update", cp)
}
