package api

import (
	"sort"
	"sync"
	"time"

	"sh.simple/simpled/internal/agents"
	"sh.simple/simpled/internal/detect"
	"sh.simple/simpled/internal/inbox"
	"sh.simple/simpled/internal/wire"
)

type PromptTracker struct {
	agents *agents.Store
	inbox  *inbox.Store
	mu     sync.Mutex
	state  map[string]*promptState
}

type promptState struct {
	kind            string
	lastText        string
	fingerprint     string
	openItemID      string
	pendingChange   string
	debounceStarted time.Time
	stableText      string
	stablePolls     int
}

func NewPromptTracker(agentStore *agents.Store, inboxStore *inbox.Store) *PromptTracker {
	return &PromptTracker{
		agents: agentStore,
		inbox:  inboxStore,
		state:  make(map[string]*promptState),
	}
}

func (p *PromptTracker) OnScreen(agentID, text string) {
	if p == nil || p.inbox == nil || p.agents == nil {
		return
	}
	agent, ok := p.agents.Get(agentID)
	if !ok {
		return
	}
	p.mu.Lock()
	st, ok := p.state[agentID]
	if !ok {
		st = &promptState{kind: screenDetectorKind(agent)}
		p.state[agentID] = st
		p.adoptOpenLocked(agentID, st)
	}
	if text == st.lastText {
		p.mu.Unlock()
		return
	}
	st.pendingChange = text
	st.stablePolls = 0
	st.stableText = ""
	if st.debounceStarted.IsZero() {
		st.debounceStarted = time.Now()
	}
	p.mu.Unlock()
}

func (p *PromptTracker) Tick() {
	if p == nil {
		return
	}
	now := time.Now()
	p.mu.Lock()
	defer p.mu.Unlock()
	for agentID, st := range p.state {
		if st.pendingChange == "" {
			continue
		}
		if now.Sub(st.debounceStarted) < 600*time.Millisecond {
			continue
		}
		text := st.pendingChange
		if text != st.stableText {
			st.stableText = text
			st.stablePolls = 1
			continue
		}
		st.stablePolls++
		if st.stablePolls < 2 {
			continue
		}
		st.pendingChange = ""
		st.debounceStarted = time.Time{}
		st.stableText = ""
		st.stablePolls = 0
		st.lastText = text
		p.applyLocked(agentID, st, text)
	}
}

func (p *PromptTracker) adoptOpenLocked(agentID string, st *promptState) {
	open := p.inbox.OpenPromptsForAgent(agentID)
	if len(open) == 0 {
		return
	}
	sort.Slice(open, func(i, j int) bool {
		return open[i].CreatedMs > open[j].CreatedMs
	})
	newest := open[0]
	for _, it := range open[1:] {
		p.inbox.Resolve(it.ID)
	}
	st.openItemID = newest.ID
	st.fingerprint = detect.FingerprintForItem(newest)
}

func (p *PromptTracker) applyLocked(agentID string, st *promptState, text string) {
	result := detect.ParseForKind(st.kind, text)
	fp := detect.Fingerprint(result)
	if result == nil {
		if st.openItemID != "" {
			p.inbox.Resolve(st.openItemID)
			st.openItemID = ""
		}
		st.fingerprint = ""
		return
	}
	if fp == st.fingerprint && st.openItemID != "" {
		return
	}
	if st.openItemID != "" {
		p.inbox.Resolve(st.openItemID)
		st.openItemID = ""
	}
	id, err := inbox.NewItemID()
	if err != nil {
		return
	}
	item := detect.ToItem(agentID, id, inbox.NowMs(), result)
	_ = p.inbox.Add(item)
	st.openItemID = id
	st.fingerprint = fp
}

func (p *PromptTracker) Stop(agentID string) {
}

func screenDetectorKind(a wire.Agent) string {
	if a.Runtime == "herdr" {
		return "herdr"
	}
	return a.Kind
}
