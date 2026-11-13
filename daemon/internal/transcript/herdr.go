package transcript

import (
	"sh.simple/simpled/internal/agents"
	"sh.simple/simpled/internal/wire"
)

type herdrLister interface {
	Snapshot() []wire.Agent
}

func (m *Manager) SyncHerdrAgents(store herdrLister) {
	if store == nil {
		return
	}
	for _, a := range store.Snapshot() {
		if a.Runtime != "herdr" || a.Session == nil || *a.Session == "" {
			continue
		}
		path := m.PathForAgent(a.ID, a, "")
		if path == "" || m.boundTo(a.ID, *a.Session, path) {
			continue
		}
		m.BindSession(a.ID, *a.Session, a.Cwd, path, a.Kind)
	}
}

func herdrClaudePath(a wire.Agent) string {
	if a.Runtime != "herdr" || a.Kind != "claude" || a.Session == nil || *a.Session == "" {
		return ""
	}
	return agents.TranscriptPath(*a.Session, a.Cwd)
}

func (m *Manager) boundTo(terminal, session, path string) bool {
	m.mu.Lock()
	defer m.mu.Unlock()
	meta := m.sessions[terminal]
	return meta != nil && meta.session == session && meta.path == path
}
