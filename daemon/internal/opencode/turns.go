package opencode

import (
	"context"
	"encoding/json"
	"strings"
	"sync"
	"time"

	"sh.simple/simpled/internal/inbox"
	"sh.simple/simpled/internal/wire"
)

type TurnAPI interface {
	ActiveSessions(ctx context.Context) (map[string]json.RawMessage, error)
	Messages(ctx context.Context, sessionID string) ([]Item, error)
}

type Turns struct {
	API       TurnAPI
	Inbox     InboxSink
	Agents    func() []wire.Agent
	SetStatus func(agentID, status string)
	Observe   func(agentID string, items []Item) bool
	Every     time.Duration

	mu   sync.Mutex
	busy map[string]bool
}

func (t *Turns) Run(ctx context.Context) {
	every := t.Every
	if every <= 0 {
		every = time.Second
	}
	tick := time.NewTicker(every)
	defer tick.Stop()
	for {
		select {
		case <-ctx.Done():
			return
		case <-tick.C:
			t.Poll(ctx)
		}
	}
}

func (t *Turns) Poll(ctx context.Context) {
	var agents []wire.Agent
	for _, a := range t.Agents() {
		if openCodeAgent(a) {
			agents = append(agents, a)
		}
	}
	if len(agents) == 0 {
		return
	}
	actx, cancel := context.WithTimeout(ctx, 3*time.Second)
	active, err := t.API.ActiveSessions(actx)
	cancel()
	if err != nil {
		return
	}
	t.mu.Lock()
	if t.busy == nil {
		t.busy = make(map[string]bool)
	}
	seen := make(map[string]bool, len(agents))
	var started, finished []wire.Agent
	for _, a := range agents {
		seen[a.ID] = true
		_, now := active[*a.Session]
		was := t.busy[a.ID]
		t.busy[a.ID] = now
		switch {
		case now && !was:
			started = append(started, a)
		case !now && was:
			finished = append(finished, a)
		}
	}
	for id := range t.busy {
		if !seen[id] {
			delete(t.busy, id)
		}
	}
	t.mu.Unlock()
	for _, a := range started {
		t.SetStatus(a.ID, "working")
	}
	for _, a := range finished {
		t.finish(ctx, a)
	}
}

func (t *Turns) finish(ctx context.Context, a wire.Agent) {
	mctx, cancel := context.WithTimeout(ctx, 4*time.Second)
	items, _ := t.API.Messages(mctx, *a.Session)
	cancel()
	if t.Observe != nil && t.Observe(a.ID, items) {
		return
	}
	t.SetStatus(a.ID, "done")
	body := LastReply(items)
	id, err := inbox.NewItemID()
	if err != nil {
		return
	}
	_ = t.Inbox.Add(wire.Item{ID: id, Agent: a.ID, Kind: "done", Title: doneTitle(body), Body: &body, CreatedMs: inbox.NowMs()})
}

func doneTitle(reply string) string {
	if strings.HasPrefix(reply, stoppedPrefix) {
		return "Stopped"
	}
	return "Finished"
}

func LastReply(items []Item) string {
	msgs := ToWire(items)
	for i := len(msgs) - 1; i >= 0; i-- {
		if msgs[i].Role == "assistant" && msgs[i].Text != nil && strings.TrimSpace(*msgs[i].Text) != "" {
			return *msgs[i].Text
		}
	}
	return ""
}

func openCodeAgent(a wire.Agent) bool {
	return a.Kind == "opencode" && a.Session != nil && *a.Session != ""
}
