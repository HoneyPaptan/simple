package transcript

import (
	"context"
	"crypto/sha256"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"sync"
	"time"

	"sh.simple/simpled/internal/agents"
	"sh.simple/simpled/internal/opencode"
	"sh.simple/simpled/internal/wire"
)

type Manager struct {
	bindingsPath  string
	mu            sync.Mutex
	sessions      map[string]*sessionMeta
	watchers      map[string]int
	pub           func(string, any)
	lookup        func(string) (wire.Agent, bool)
	onContext     func(string, *wire.AgentContext)
	onModel       func(string, string)
	onPromptTitle func(string, string)
	pollIDs       func() []string

	openCode       *opencode.Client
	openCodeSums   map[string][32]byte
	openCodeLimits map[string]int
	ctxKeys        map[string]string

	setAgentStatus func(string, string)
	onCursorDone   func(string, string)
	cursorAgents   map[string]*cursorTrack
	cursorSeen     map[string]map[string]int64
}

type sessionMeta struct {
	terminal  string
	session   string
	cwd       string
	path      string
	kind      string
	startedMs int64
}

func NewManager(publish func(string, any)) *Manager {
	return &Manager{
		sessions: make(map[string]*sessionMeta),
		watchers: make(map[string]int),
		pub:      publish,

		openCode:       opencode.NewClient(),
		openCodeSums:   make(map[string][32]byte),
		openCodeLimits: make(map[string]int),
	}
}

func (m *Manager) SetModelSync(onModel func(string, string)) {
	m.mu.Lock()
	m.onModel = onModel
	m.mu.Unlock()
}

func (m *Manager) SetPromptTitleSync(onPromptTitle func(string, string)) {
	m.mu.Lock()
	m.onPromptTitle = onPromptTitle
	m.mu.Unlock()
}

func (m *Manager) SetContextSync(onContext func(string, *wire.AgentContext), pollIDs func() []string) {
	m.mu.Lock()
	m.onContext = onContext
	m.pollIDs = pollIDs
	m.mu.Unlock()
}

func (m *Manager) SetAgentLookup(lookup func(string) (wire.Agent, bool)) {
	m.mu.Lock()
	m.lookup = lookup
	m.mu.Unlock()
}

func (m *Manager) tailTarget(agentID string) (string, string) {
	m.mu.Lock()
	meta := m.sessions[agentID]
	lookup := m.lookup
	m.mu.Unlock()
	if meta != nil && meta.path != "" && (meta.kind != "cursor" || lookup == nil) {
		return meta.path, meta.kind
	}
	if lookup == nil {
		return "", ""
	}
	agent, ok := lookup(agentID)
	if !ok {
		if meta != nil {
			return meta.path, meta.kind
		}
		return "", ""
	}
	return m.PathForAgent(agentID, agent, ""), agent.Kind
}

func (m *Manager) BindSession(terminal, session, cwd, transcriptPath, kind string) {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.sessions[terminal] = &sessionMeta{
		terminal: terminal, session: session, cwd: cwd, path: transcriptPath, kind: kind,
		startedMs: time.Now().UnixMilli(),
	}
	m.saveBindingsLocked()
}

func (m *Manager) PathForAgent(terminal string, agent wire.Agent, hookPath string) string {
	if hookPath != "" {
		return hookPath
	}
	if path := herdrClaudePath(agent); path != "" {
		return path
	}
	m.mu.Lock()
	meta := m.sessions[terminal]
	m.mu.Unlock()
	if meta != nil && meta.path != "" && meta.kind != "cursor" {
		return meta.path
	}
	if agent.Session != nil && *agent.Session != "" {
		if path := agents.TranscriptPath(*agent.Session, agent.Cwd); fileExists(path) || agent.Kind != "claude" {
			return path
		}
	}
	switch agent.Kind {
	case "claude":
		return m.newestJSONL(agent.Cwd, agent.StartedMs)
	case "cursor":
		return m.cursorPathFor(terminal, agent.Cwd, agent.StartedMs)
	default:
		return ""
	}
}

func (m *Manager) cursorPathFor(terminal, cwd string, afterMs int64) string {
	m.mu.Lock()
	bound := ""
	if meta := m.sessions[terminal]; meta != nil {
		bound = meta.path
	}
	claimed := make(map[string]struct{})
	for id, meta := range m.sessions {
		if id == terminal || meta.path == "" {
			continue
		}
		if meta.kind == "cursor" {
			claimed[meta.path] = struct{}{}
		}
	}
	m.mu.Unlock()
	if bound != "" {
		if next, ok := agents.NewerCursorTranscript(agents.CursorTranscripts(cwd), bound, claimed); ok {
			m.BindSession(terminal, next.Session, cwd, next.Path, "cursor")
			return next.Path
		}
		return bound
	}
	path, session := agents.FirstCursorTranscript(cwd, afterMs, claimed)
	if path == "" {
		latest, ok := agents.LatestCursorTranscript(agents.CursorTranscripts(cwd), claimed)
		if !ok {
			return ""
		}
		path, session = latest.Path, latest.Session
	}
	m.BindSession(terminal, session, cwd, path, "cursor")
	return path
}

func (m *Manager) newestJSONL(cwd string, afterMs int64) string {
	dir := agents.ClaudeProjectDir(cwd)
	entries, err := os.ReadDir(dir)
	if err != nil {
		return ""
	}
	type cand struct {
		path string
		mod  time.Time
	}
	var best cand
	for _, e := range entries {
		if e.IsDir() || !stringsHasSuffix(e.Name(), ".jsonl") {
			continue
		}
		info, err := e.Info()
		if err != nil {
			continue
		}
		if info.ModTime().UnixMilli() < afterMs {
			continue
		}
		if best.path == "" || info.ModTime().After(best.mod) {
			best = cand{filepath.Join(dir, e.Name()), info.ModTime()}
		}
	}
	return best.path
}

func stringsHasSuffix(s, suf string) bool {
	return len(s) >= len(suf) && s[len(s)-len(suf):] == suf
}

func (m *Manager) SetWatch(agentID string, on bool) {
	m.mu.Lock()
	if on {
		m.watchers[agentID]++
	} else if m.watchers[agentID] > 0 {
		m.watchers[agentID]--
		if m.watchers[agentID] == 0 {
			delete(m.watchers, agentID)
		}
	}
	m.mu.Unlock()
}

const tailEvery = 500 * time.Millisecond

func (m *Manager) Run(ctx context.Context) {
	ticker := time.NewTicker(tailEvery)
	defer ticker.Stop()
	lastSize := map[string]int64{}
	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			m.poll(lastSize)
		}
	}
}

func (m *Manager) poll(lastSize map[string]int64) {
	m.mu.Lock()
	seen := make(map[string]struct{}, len(m.watchers)+8)
	for id := range m.watchers {
		seen[id] = struct{}{}
	}
	if m.pollIDs != nil {
		for _, id := range m.pollIDs() {
			seen[id] = struct{}{}
		}
	}
	agents := make([]string, 0, len(seen))
	for id := range seen {
		agents = append(agents, id)
	}
	m.mu.Unlock()
	for _, agentID := range agents {
		m.tailAgent(agentID, lastSize)
	}
}

func (m *Manager) tailAgent(agentID string, lastSize map[string]int64) {
	agent, haveAgent := m.agentFor(agentID)
	if haveAgent {
		if session := openCodeSession(agent); session != "" {
			m.refreshOpenCode(agentID, agent, session, lastSize)
			return
		}
	}
	path, kind := m.tailTarget(agentID)
	if path == "" {
		return
	}
	st, err := os.Stat(path)
	if err != nil {
		return
	}
	if kind == "cursor" {
		m.pollCursorStatus(agentID, path, st.Size(), time.Now())
	}
	changed := st.Size() != lastSize[path]
	if changed {
		lastSize[path] = st.Size()
	}
	if haveAgent && m.contextStale(agentID, agent, path, st.Size()) {
		m.syncFileContext(agentID, agent, path, kind)
	}
	if !changed {
		return
	}
	msgs, err := m.parseFor(path, kind)
	if err != nil || len(msgs) == 0 {
		return
	}
	start := 0
	if len(msgs) > 20 {
		start = len(msgs) - 20
	}
	m.pub("transcript.append", wire.TranscriptAppend{ID: agentID, Messages: msgs[start:]})
}

func (m *Manager) ReadPage(agentID string, agent wire.Agent, before *string, limit int) ([]wire.Message, *string, int64, error) {
	var msgs []wire.Message
	if session := openCodeSession(agent); session != "" {
		all, err := m.openCodeMessages(session)
		if err != nil {
			return nil, nil, 0, err
		}
		m.applyOpenCodeContext(agentID, agent, session)
		msgs = all
	} else {
		path := m.PathForAgent(agentID, agent, "")
		if path == "" {
			return nil, nil, 0, nil
		}
		m.syncFileContext(agentID, agent, path, agent.Kind)
		all, err := m.parseFor(path, agent.Kind)
		if err != nil {
			return nil, nil, 0, err
		}
		msgs = all
	}
	out, next := PageMessages(msgs, before, limit)
	return out, next, chatSince(msgs), nil
}

func chatSince(msgs []wire.Message) int64 {
	var since int64
	for _, msg := range msgs {
		if msg.Ts > 0 && (since == 0 || msg.Ts < since) {
			since = msg.Ts
		}
	}
	return since
}

func (m *Manager) refreshOpenCode(agentID string, agent wire.Agent, session string, lastSize map[string]int64) {
	changed := false
	m.mu.Lock()
	same := m.openCodeSums[agentID]
	m.mu.Unlock()
	msgs, err := m.openCodeMessages(session)
	if err == nil && len(msgs) > 0 {
		raw, _ := json.Marshal(msgs)
		sum := sha256.Sum256(raw)
		changed = sum != same
		if changed {
			m.mu.Lock()
			m.openCodeSums[agentID] = sum
			m.mu.Unlock()
			start := 0
			if len(msgs) > 20 {
				start = len(msgs) - 20
			}
			m.pub("transcript.append", wire.TranscriptAppend{ID: agentID, Messages: msgs[start:]})
		}
	}
	m.applyOpenCodeContext(agentID, agent, session)
}

func (m *Manager) applyOpenCodeContext(agentID string, agent wire.Agent, session string) {
	ctx, model, err := m.contextFromOpenCode(session, agent)
	if err != nil {
		return
	}
	m.pushContext(agentID, ctx)
	m.mu.Lock()
	onModel := m.onModel
	m.mu.Unlock()
	if onModel != nil && model != "" {
		onModel(agentID, model)
	}
}

func (m *Manager) contextStale(agentID string, agent wire.Agent, path string, size int64) bool {
	model := ""
	if agent.Model != nil {
		model = *agent.Model
	}
	key := fmt.Sprintf("%s|%d|%s", path, size, model)
	if m.ctxKeys == nil {
		m.ctxKeys = make(map[string]string)
	}
	if m.ctxKeys[agentID] == key {
		return false
	}
	m.ctxKeys[agentID] = key
	return true
}

func (m *Manager) syncFileContext(agentID string, agent wire.Agent, path, kind string) {
	var ctx *wire.AgentContext
	var err error
	switch kind {
	case "claude":
		ctx, err = ContextFromClaudeJSONL(path, agent)
		m.syncPromptTitle(agentID, path, kind)
	case "codex":
		ctx, err = ContextFromCodexRollout(path)
	default:
		return
	}
	if err != nil {
		return
	}
	m.pushContext(agentID, ctx)
}

func (m *Manager) syncPromptTitle(agentID, path, kind string) {
	m.mu.Lock()
	fn := m.onPromptTitle
	m.mu.Unlock()
	if fn == nil {
		return
	}
	text, err := FirstUserPrompt(path, kind)
	if err != nil || text == "" {
		return
	}
	fn(agentID, text)
}

func (m *Manager) pushContext(agentID string, ctx *wire.AgentContext) {
	m.mu.Lock()
	fn := m.onContext
	m.mu.Unlock()
	if fn == nil {
		return
	}
	fn(agentID, ctx)
}

func fileExists(path string) bool {
	_, err := os.Stat(path)
	return err == nil
}
