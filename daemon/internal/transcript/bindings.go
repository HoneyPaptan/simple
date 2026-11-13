package transcript

import (
	"encoding/json"
	"os"
	"path/filepath"
	"time"
)

type savedBinding struct {
	Terminal string `json:"terminal"`
	Session  string `json:"session"`
	Cwd      string `json:"cwd"`
	Path     string `json:"path"`
	Kind     string `json:"kind"`
}

func (m *Manager) PersistBindings(path string) {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.bindingsPath = path
	raw, err := os.ReadFile(path)
	if err != nil {
		return
	}
	var saved []savedBinding
	if json.Unmarshal(raw, &saved) != nil {
		return
	}
	now := time.Now().UnixMilli()
	for _, b := range saved {
		if b.Terminal == "" || m.sessions[b.Terminal] != nil || !fileExists(b.Path) {
			continue
		}
		m.sessions[b.Terminal] = &sessionMeta{
			terminal: b.Terminal, session: b.Session, cwd: b.Cwd, path: b.Path, kind: b.Kind, startedMs: now,
		}
	}
}

func (m *Manager) saveBindingsLocked() {
	if m.bindingsPath == "" {
		return
	}
	saved := make([]savedBinding, 0, len(m.sessions))
	for _, meta := range m.sessions {
		if meta.path == "" {
			continue
		}
		saved = append(saved, savedBinding{
			Terminal: meta.terminal, Session: meta.session, Cwd: meta.cwd, Path: meta.path, Kind: meta.kind,
		})
	}
	raw, err := json.Marshal(saved)
	if err != nil {
		return
	}
	tmp := m.bindingsPath + ".tmp"
	if os.MkdirAll(filepath.Dir(m.bindingsPath), 0o700) != nil || os.WriteFile(tmp, raw, 0o600) != nil {
		return
	}
	_ = os.Rename(tmp, m.bindingsPath)
}
