package agents

import (
	"strings"
	"time"

	"sh.simple/simpled/internal/herdr"
	"sh.simple/simpled/internal/wire"
)

const herdrIDPrefix = "h:"

func HerdrIDPrefix() string { return herdrIDPrefix }

type herdrRecord struct {
	target string
	info   herdr.AgentInfo
}

func HerdrAgentID(paneID string) string {
	return herdrIDPrefix + paneID
}

func IsHerdrAgentID(id string) bool {
	return strings.HasPrefix(id, herdrIDPrefix)
}

func (s *Store) HerdrTarget(id string) (string, bool) {
	s.mu.Lock()
	defer s.mu.Unlock()
	rec, ok := s.herdr[id]
	if !ok {
		return "", false
	}
	if rec.info.Name != "" {
		return rec.info.Name, true
	}
	return rec.info.PaneID, true
}

func (s *Store) AgentRuntime(id string) string {
	s.mu.Lock()
	defer s.mu.Unlock()
	if a, ok := s.byID[id]; ok && a.Runtime != "" {
		return a.Runtime
	}
	if IsHerdrAgentID(id) {
		return "herdr"
	}
	return "hc"
}

func MapHerdrKind(agent string) string {
	switch strings.ToLower(agent) {
	case "claude":
		return "claude"
	case "codex":
		return "codex"
	case "opencode":
		return "opencode"
	case "cursor":
		return "cursor"
	case "fx":
		return "fx"
	default:
		if agent == "" {
			return "other"
		}
		return "shell"
	}
}

func MapHerdrStatus(st string) (status string, waiting *string) {
	switch strings.ToLower(st) {
	case "working":
		return "working", nil
	case "idle":
		return "idle", nil
	case "done":
		return "done", nil
	case "blocked":
		msg := "Needs you"
		return "waiting", &msg
	case "unknown":
		return "unknown", nil
	default:
		return "unknown", nil
	}
}

func buildHerdrAgent(info herdr.AgentInfo) wire.Agent {
	kind := MapHerdrKind(info.Agent)
	status, waiting := MapHerdrStatus(info.AgentStatus)
	title := strings.TrimSpace(info.Name)
	if isGeneratedHerdrName(title) {
		title = ""
	}
	if title == "" {
		title = strings.TrimSpace(info.TerminalTitleStripped)
	}
	if title == "" {
		title = KindLabel(kind)
	}
	cwd := herdrCwd(info.Cwd)
	if cwd == "" {
		cwd = herdrCwd(info.ForegroundCwd)
	}
	ws := gitRoot(cwd)
	if ws == "" {
		ws = cwd
	}
	now := time.Now().UnixMilli()
	var sess *string
	if info.AgentSession != nil && info.AgentSession.Value != "" && info.AgentSession.Kind == "id" {
		v := info.AgentSession.Value
		sess = &v
	}
	return wire.Agent{
		ID:        HerdrAgentID(info.PaneID),
		Runtime:   "herdr",
		Kind:      kind,
		Title:     title,
		Cwd:       cwd,
		Workspace: ws,
		Status:    status,
		Waiting:   waiting,
		StartedMs: now,
		UpdatedMs: now,
		Session:   sess,
	}
}

func herdrCwd(raw string) string {
	return herdr.CleanCwd(raw)
}

func (s *Store) LoadHerdrAgents(list []herdr.AgentInfo) {
	s.mu.Lock()
	next := make(map[string]herdrRecord)
	nextAgents := make(map[string]*wire.Agent)
	for _, info := range list {
		if info.Agent == "" {
			continue
		}
		id := HerdrAgentID(info.PaneID)
		a := buildHerdrAgent(info)
		s.applyMeta(&a, s.meta[id])
		s.applyPromptTitle(&a, s.meta[id])
		if old, ok := s.byID[id]; ok && old.StartedMs > 0 {
			a.StartedMs = old.StartedMs
		} else if saved, ok := s.herdrSaved[id]; ok {
			a.StartedMs = saved.StartedMs
			if a.Session == nil {
				a.Session = saved.Session
			}
			if saved.Status == a.Status && saved.UpdatedMs > 0 {
				a.UpdatedMs = saved.UpdatedMs
			}
		}
		if old, ok := s.byID[id]; ok {
			if old.Status == a.Status {
				a.UpdatedMs = old.UpdatedMs
			}
			if a.Session == nil {
				a.Session = old.Session
			}
			a.Model = old.Model
			a.Mode = old.Mode
			a.Effort = old.Effort
			a.Pinned = old.Pinned
			a.Unread = old.Unread
		}
		next[id] = herdrRecord{target: targetFor(info), info: info}
		nextAgents[id] = &a
	}
	for id, old := range s.byID {
		if old.Runtime != "herdr" {
			continue
		}
		if _, ok := nextAgents[id]; !ok {
			s.pub("agent.removed", wire.AgentRemoved{ID: id})
			delete(s.meta, id)
			delete(s.herdr, id)
			delete(s.byID, id)
		}
	}
	for id, a := range nextAgents {
		old, had := s.byID[id]
		s.byID[id] = a
		s.herdr[id] = next[id]
		if !had || !agentEqual(old, a) {
			s.pub("agent.update", *a)
		}
	}
	s.reconcileWorkspacesLocked()
	s.saveHerdrLocked()
	s.mu.Unlock()
	s.bumpPollSync()
}

func targetFor(info herdr.AgentInfo) string {
	if info.Name != "" {
		return info.Name
	}
	return info.PaneID
}

func isGeneratedHerdrName(name string) bool {
	if len(name) != 9 || name[0] != 's' {
		return false
	}
	for _, c := range name[1:] {
		if !(c >= '0' && c <= '9' || c >= 'a' && c <= 'f') {
			return false
		}
	}
	return true
}
