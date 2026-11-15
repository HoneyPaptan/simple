package agents

import (
	"encoding/json"
	"os"
	"path/filepath"
	"regexp"
	"strings"
	"sync"
	"time"

	"sh.simple/simpled/internal/hc"
	"sh.simple/simpled/internal/hub"
	"sh.simple/simpled/internal/wire"
)

type Store struct {
	mu           sync.Mutex
	byID         map[string]*wire.Agent
	terms        map[string]hc.Terminal
	herdr        map[string]herdrRecord
	meta         map[string]userMeta
	promptTitles map[string]string
	wsCounts     map[string]int

	herdrSaved     map[string]herdrSaved
	herdrSavedPath string
	sendFail       map[string]sendFail

	pub func(ev string, data any)

	onPollSync func()
}

type userMeta struct {
	title  string
	pinned bool
	unread bool
	has    bool
}

func NewStore(publish func(string, any)) *Store {
	return &Store{
		byID:         make(map[string]*wire.Agent),
		terms:        make(map[string]hc.Terminal),
		herdr:        make(map[string]herdrRecord),
		meta:         make(map[string]userMeta),
		promptTitles: make(map[string]string),
		wsCounts:     make(map[string]int),
		sendFail:     make(map[string]sendFail),
		pub:          publish,
	}
}

func (s *Store) SetPollSync(fn func()) {
	s.mu.Lock()
	s.onPollSync = fn
	s.mu.Unlock()
}

func (s *Store) PollableAgentIDs() []string {
	s.mu.Lock()
	defer s.mu.Unlock()
	out := make([]string, 0, len(s.byID))
	for id, a := range s.byID {
		if a.Status == "failed" {
			lat, ok := s.sendFail[id]
			if !ok || !lat.fromSend {
				continue
			}
		}
		if a.Runtime == "herdr" {
			out = append(out, id)
			continue
		}
		t, ok := s.terms[id]
		if ok && t.State == "finished" {
			continue
		}
		out = append(out, id)
	}
	return out
}

func (s *Store) bumpPollSync() {
	s.mu.Lock()
	fn := s.onPollSync
	s.mu.Unlock()
	if fn != nil {
		fn()
	}
}

func (s *Store) Snapshot() []wire.Agent {
	s.mu.Lock()
	defer s.mu.Unlock()
	out := make([]wire.Agent, 0, len(s.byID))
	for _, a := range s.byID {
		out = append(out, *a)
	}
	return out
}

func (s *Store) Get(id string) (wire.Agent, bool) {
	s.mu.Lock()
	defer s.mu.Unlock()
	a, ok := s.byID[id]
	if !ok {
		return wire.Agent{}, false
	}
	return *a, true
}

func (s *Store) ApplyUserUpdate(id string, title *string, pinned, unread *bool) (wire.Agent, bool) {
	s.mu.Lock()
	defer s.mu.Unlock()
	a, ok := s.byID[id]
	if !ok {
		return wire.Agent{}, false
	}
	m := s.meta[id]
	if title != nil {
		m.title = *title
		m.has = true
	}
	if pinned != nil {
		m.pinned = *pinned
		m.has = true
	}
	if unread != nil {
		m.unread = *unread
		m.has = true
	}
	s.meta[id] = m
	s.applyMeta(a, m)
	s.applyPromptTitle(a, m)
	now := time.Now().UnixMilli()
	a.UpdatedMs = now
	cp := *a
	s.pub("agent.update", cp)
	return cp, true
}

func (s *Store) LoadSnapshot(terminals []hc.Terminal) {
	s.mu.Lock()
	next := make(map[string]hc.Terminal)
	nextAgents := make(map[string]*wire.Agent)
	for _, t := range terminals {
		id := t.Address
		next[id] = t
		a := s.buildAgent(t)
		s.applyMeta(&a, s.meta[id])
		s.applyPromptTitle(&a, s.meta[id])
		nextAgents[id] = &a
	}
	for id, old := range s.byID {
		if old.Runtime == "herdr" {
			continue
		}
		if _, ok := nextAgents[id]; !ok {
			s.pub("agent.removed", wire.AgentRemoved{ID: id})
			delete(s.meta, id)
			delete(s.byID, id)
			delete(s.terms, id)
			delete(s.sendFail, id)
		}
	}
	for id, a := range nextAgents {
		old, had := s.byID[id]
		s.holdSendFail(a)
		s.byID[id] = a
		s.terms[id] = next[id]
		if !had || !agentEqual(old, a) {
			s.pub("agent.update", *a)
		}
	}
	s.reconcileWorkspacesLocked()
	s.mu.Unlock()
	s.bumpPollSync()
}

func (s *Store) HandleEvent(ev hc.Event) {
	s.mu.Lock()
	defer func() {
		s.mu.Unlock()
		s.bumpPollSync()
	}()
	switch ev.Type {
	case "created":
		var t hc.Terminal
		if err := json.Unmarshal(ev.Data, &t); err != nil {
			return
		}
		t.Address = ev.Terminal
		s.upsertLocked(t)
	case "updated":
		t, ok := s.terms[ev.Terminal]
		if !ok {
			var nt hc.Terminal
			if json.Unmarshal(ev.Data, &nt) == nil {
				nt.Address = ev.Terminal
				s.upsertLocked(nt)
			}
			return
		}
		_ = hc.MergeTerminal(&t, ev.Data)
		s.terms[ev.Terminal] = t
		s.upsertLocked(t)
	case "finished":
		t, ok := s.terms[ev.Terminal]
		if !ok {
			var nt hc.Terminal
			if json.Unmarshal(ev.Data, &nt) == nil {
				nt.Address = ev.Terminal
				s.upsertLocked(nt)
			}
			return
		}
		_ = hc.MergeTerminal(&t, ev.Data)
		t.State = "finished"
		s.terms[ev.Terminal] = t
		s.upsertLocked(t)
	case "removed":
		if _, ok := s.byID[ev.Terminal]; ok {
			delete(s.byID, ev.Terminal)
			delete(s.terms, ev.Terminal)
			delete(s.meta, ev.Terminal)
			delete(s.sendFail, ev.Terminal)
			s.pub("agent.removed", wire.AgentRemoved{ID: ev.Terminal})
			s.reconcileWorkspacesLocked()
		}
	}
}

func (s *Store) upsertLocked(t hc.Terminal) {
	a := s.buildAgent(t)
	s.applyMeta(&a, s.meta[t.Address])
	s.applyPromptTitle(&a, s.meta[t.Address])
	old, had := s.byID[t.Address]
	if had {
		carryDerived(&a, old)
	}
	s.holdSendFail(&a)
	s.byID[t.Address] = &a
	s.terms[t.Address] = t
	if !had || !agentEqual(old, &a) {
		s.pub("agent.update", a)
	}
	s.reconcileWorkspacesLocked()
}

func carryDerived(a, old *wire.Agent) {
	if a.Context == nil {
		a.Context = old.Context
	}
	if a.Kind == "opencode" && old.Model != nil && (a.Model == nil || *a.Model == "default") {
		a.Model = old.Model
	}
}

func (s *Store) applyMeta(a *wire.Agent, m userMeta) {
	if m.has {
		if m.title != "" {
			a.Title = m.title
		}
		a.Pinned = m.pinned
		a.Unread = m.unread
	}
}

func (s *Store) applyPromptTitle(a *wire.Agent, m userMeta) {
	if m.has && m.title != "" {
		return
	}
	if !DefaultKindTitle(a.Kind, a.Title) {
		return
	}
	if pt := s.promptTitles[a.ID]; pt != "" {
		a.Title = pt
	}
}

func (s *Store) SetAgentPromptTitle(id, raw string) {
	title := CompactPromptTitle(raw)
	if title == "" {
		return
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.promptTitles[id] == title {
		return
	}
	s.promptTitles[id] = title
	a, ok := s.byID[id]
	if !ok {
		return
	}
	m := s.meta[id]
	if m.has && m.title != "" {
		return
	}
	if !DefaultKindTitle(a.Kind, a.Title) {
		return
	}
	a.Title = title
	a.UpdatedMs = time.Now().UnixMilli()
	cp := *a
	s.pub("agent.update", cp)
}

func agentEqual(a, b *wire.Agent) bool {
	if a == nil || b == nil {
		return a == b
	}
	ax, ay := *a, *b
	ax.UpdatedMs, ay.UpdatedMs = 0, 0
	ab, _ := json.Marshal(ax)
	bb, _ := json.Marshal(ay)
	return string(ab) == string(bb)
}

func (s *Store) Workspaces() []wire.Workspace {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.workspacesLocked()
}

func (s *Store) workspacesLocked() []wire.Workspace {
	acc := map[string]*wire.Workspace{}
	for _, a := range s.byID {
		ws := a.Workspace
		if ws == "" {
			continue
		}
		w, ok := acc[ws]
		if !ok {
			name := filepath.Base(ws)
			w = &wire.Workspace{
				Path:      ws,
				Name:      name,
				UpdatedMs: a.UpdatedMs,
			}
			acc[ws] = w
		}
		w.Agents++
		if a.UpdatedMs > w.UpdatedMs {
			w.UpdatedMs = a.UpdatedMs
		}
		if a.Pinned {
			w.Pinned = true
		}
	}
	out := make([]wire.Workspace, 0, len(acc))
	for _, w := range acc {
		out = append(out, *w)
	}
	return out
}

func (s *Store) reconcileWorkspacesLocked() {
	curr := s.workspacesLocked()
	seen := map[string]bool{}
	for _, w := range curr {
		seen[w.Path] = true
		prev, had := s.wsCounts[w.Path]
		if !had || prev != w.Agents {
			s.pub("workspace.update", w)
		}
	}
	now := time.Now().UnixMilli()
	for path, prev := range s.wsCounts {
		if seen[path] || prev == 0 {
			continue
		}
		s.pub("workspace.update", wire.Workspace{
			Path:      path,
			Name:      filepath.Base(path),
			Agents:    0,
			UpdatedMs: now,
		})
	}
	s.wsCounts = make(map[string]int, len(curr))
	for _, w := range curr {
		s.wsCounts[w.Path] = w.Agents
	}
}

func DetectKind(t hc.Terminal) string {
	if t.Labels != nil {
		if k, ok := t.Labels["simple.kind"]; ok && k != "" {
			return normalizeKind(k)
		}
		if a, ok := t.Labels["agent"]; ok {
			return normalizeKind(a)
		}
	}
	argv := t.Argv
	if t.Foreground != nil && len(t.Foreground.Argv) > 0 {
		argv = t.Foreground.Argv
	}
	if len(argv) == 0 {
		return "other"
	}
	return normalizeKind(argv[0])
}

func normalizeKind(s string) string {
	s = strings.ToLower(filepath.Base(s))
	switch {
	case strings.Contains(s, "claude"):
		return "claude"
	case strings.Contains(s, "cursor"):
		return "cursor"
	case strings.Contains(s, "opencode"):
		return "opencode"
	case strings.Contains(s, "codex"):
		return "codex"
	case s == "gemini":
		return "gemini"
	case strings.HasPrefix(s, "qwen"):
		return "qwen"
	case s == "copilot":
		return "copilot"
	case s == "amp":
		return "amp"
	case s == "goose":
		return "goose"
	case s == "crush":
		return "crush"
	case s == "aider":
		return "aider"
	case s == "kimi":
		return "kimi"
	case s == "fx":
		return "fx"
	case s == "sh", s == "bash", s == "zsh", s == "fish", s == "shell":
		return "shell"
	default:
		return "other"
	}
}

func MapStatus(t hc.Terminal) (status string, waiting *string) {
	if t.State == "finished" {
		if t.Reason == "killed" || (t.ExitCode != nil && endedCleanly(*t.ExitCode)) {
			return "done", nil
		}
		return "failed", nil
	}
	if t.Status == nil {
		return "unknown", nil
	}
	st := strings.ToLower(t.Status.State)
	switch st {
	case "idle":
		return "idle", nil
	case "working":
		return "working", nil
	case "done":
		return "done", nil
	case "blocked", "error":
		if t.Status.Kind != nil {
			k := strings.ToLower(*t.Status.Kind)
			if k == "permission" || k == "question" || k == "auth" {
				msg := t.Status.Msg
				if msg == "" {
					msg = t.Status.Title
				}
				if msg != "" {
					return "waiting", &msg
				}
				return "waiting", nil
			}
		}
		if msg := t.Status.Msg; msg != "" && msg != "dialog open" {
			return "waiting", &msg
		}
		return "waiting", nil
	default:
		if strings.Contains(st, "waiting_for_person") {
			msg := t.Status.Msg
			return "waiting", &msg
		}
		return "unknown", nil
	}
}

func endedCleanly(code int32) bool {
	switch code {
	case 0, 129, 130, 137, 143:
		return true
	}
	return false
}

func gitRoot(cwd string) string {
	if cwd == "" {
		return ""
	}
	dir, err := filepath.Abs(cwd)
	if err != nil {
		return cwd
	}
	for {
		if _, err := os.Stat(filepath.Join(dir, ".git")); err == nil {
			return dir
		}
		parent := filepath.Dir(dir)
		if parent == dir {
			return cwd
		}
		dir = parent
	}
}

func (s *Store) buildAgent(t hc.Terminal) wire.Agent {
	kind := DetectKind(t)
	status, waiting := MapStatus(t)
	title := t.Title
	if t.Status != nil && t.Status.Title != "" {
		title = t.Status.Title
	}
	cwd := t.Cwd
	if t.Foreground != nil && t.Foreground.Cwd != "" {
		cwd = t.Foreground.Cwd
	}
	ws := gitRoot(cwd)
	if ws == "" {
		ws = cwd
	}
	now := time.Now().UnixMilli()
	started := int64(t.CreatedMs)
	if started <= 0 {
		started = now
	}
	if genericTitle(title, ws, t.Argv) {
		title = KindLabel(kind)
	}
	return wire.Agent{
		ID:        t.Address,
		Runtime:   "hc",
		Kind:      kind,
		Title:     title,
		Cwd:       cwd,
		Workspace: ws,
		Status:    status,
		Waiting:   waiting,
		StartedMs: started,
		UpdatedMs: lastActivityMs(t, started),
		Mode:      labelPtr(t.Labels, "simple.mode"),
		Model:     labelPtr(t.Labels, "simple.model"),
		Effort:    labelPtr(t.Labels, "simple.effort"),
		Session:   labelPtr(t.Labels, "simple.session"),
	}
}

func labelPtr(labels map[string]string, key string) *string {
	if labels == nil {
		return nil
	}
	v, ok := labels[key]
	if !ok || v == "" {
		return nil
	}
	return &v
}

func (s *Store) SetAgentContext(id string, ctx *wire.AgentContext) {
	s.mu.Lock()
	defer s.mu.Unlock()
	a, ok := s.byID[id]
	if !ok {
		return
	}
	if contextsEqual(a.Context, ctx) {
		return
	}
	a.Context = ctx
	cp := *a
	s.pub("agent.update", cp)
}

func (s *Store) SetAgentModel(id, model string) {
	s.mu.Lock()
	defer s.mu.Unlock()
	a, ok := s.byID[id]
	if !ok || model == "" || (a.Model != nil && *a.Model == model) {
		return
	}
	a.Model = &model
	cp := *a
	s.pub("agent.update", cp)
}

func sameWaiting(a, b *string) bool {
	if a == nil || b == nil {
		return a == nil && b == nil
	}
	return *a == *b
}

func contextsEqual(a, b *wire.AgentContext) bool {
	if a == nil && b == nil {
		return true
	}
	if a == nil || b == nil {
		return false
	}
	return a.Used == b.Used && a.Window == b.Window
}

func (s *Store) SetAgentFields(id string, status string, waiting *string) {
	s.mu.Lock()
	defer s.mu.Unlock()
	a, ok := s.byID[id]
	if !ok {
		return
	}
	if status == "working" {
		s.dropLatchLocked(id, false)
	}
	if a.Status == status && sameWaiting(a.Waiting, waiting) {
		return
	}
	a.Status = status
	a.Waiting = waiting
	a.UpdatedMs = time.Now().UnixMilli()
	cp := *a
	s.pub("agent.update", cp)
}

func (s *Store) Terminal(id string) (hc.Terminal, bool) {
	s.mu.Lock()
	defer s.mu.Unlock()
	t, ok := s.terms[id]
	return t, ok
}

func (s *Store) Upsert(a wire.Agent) {
	s.mu.Lock()
	defer s.mu.Unlock()
	cp := a
	s.byID[a.ID] = &cp
	s.pub("agent.update", a)
	s.reconcileWorkspacesLocked()
}

func PublishHostUpdate(h *hub.Hub, hcVer, herdrVer *string, linger bool) {
	h.Publish("host.update", map[string]any{
		"hc":     hcVer,
		"herdr":  herdrVer,
		"linger": linger,
	})
}

var shellPromptTitle = regexp.MustCompile(`^[^\s@]+@[^\s:]+:`)

func genericTitle(title, ws string, argv []string) bool {
	title = strings.TrimSpace(title)
	if title == "" || shellPromptTitle.MatchString(title) {
		return true
	}
	if ws != "" && (title == filepath.Base(ws) || title == ws) {
		return true
	}
	return len(argv) > 0 && (title == strings.Join(argv, " ") || title == filepath.Base(argv[0]))
}

func KindLabel(kind string) string {
	switch kind {
	case "claude":
		return "Claude"
	case "codex":
		return "Codex"
	case "cursor":
		return "Cursor"
	case "opencode":
		return "OpenCode"
	case "gemini":
		return "Gemini"
	case "qwen":
		return "Qwen"
	case "copilot":
		return "Copilot"
	case "amp":
		return "Amp"
	case "goose":
		return "Goose"
	case "crush":
		return "Crush"
	case "aider":
		return "Aider"
	case "kimi":
		return "Kimi"
	case "shell":
		return "Shell"
	case "":
		return "Terminal"
	}
	return strings.ToUpper(kind[:1]) + kind[1:]
}

func lastActivityMs(t hc.Terminal, started int64) int64 {
	last := started
	if t.StatusMs != nil && int64(*t.StatusMs) > last {
		last = int64(*t.StatusMs)
	}
	if t.FinishedMs != nil && int64(*t.FinishedMs) > last {
		last = int64(*t.FinishedMs)
	}
	return last
}
