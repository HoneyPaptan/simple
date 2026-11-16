package transcript

import (
	"time"

	"sh.simple/simpled/internal/wire"
)

const openCodeTimeout = 4 * time.Second

func (m *Manager) agentFor(agentID string) (wire.Agent, bool) {
	m.mu.Lock()
	lookup := m.lookup
	m.mu.Unlock()
	if lookup == nil {
		return wire.Agent{}, false
	}
	return lookup(agentID)
}

func openCodeSession(agent wire.Agent) string {
	if agent.Kind != "opencode" || agent.Session == nil {
		return ""
	}
	return *agent.Session
}
