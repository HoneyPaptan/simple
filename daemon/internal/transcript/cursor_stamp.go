package transcript

import (
	"time"

	"sh.simple/simpled/internal/wire"
)

func (m *Manager) stampCursor(path string, msgs []wire.Message, now time.Time) {
	m.mu.Lock()
	defer m.mu.Unlock()
	if m.cursorSeen == nil {
		m.cursorSeen = make(map[string]map[string]int64)
	}
	seen, known := m.cursorSeen[path]
	if !known {
		seen = make(map[string]int64, len(msgs))
		m.cursorSeen[path] = seen
	}
	next := now.UnixMilli()
	for i := range msgs {
		if ts, ok := seen[msgs[i].ID]; ok {
			msgs[i].Ts = ts
			continue
		}
		if known {
			msgs[i].Ts = next
			next++
		}
		seen[msgs[i].ID] = msgs[i].Ts
	}
}

func (m *Manager) parseFor(path, kind string) ([]wire.Message, error) {
	msgs, err := parseTranscriptFile(path, kind)
	if err == nil && kind == "cursor" {
		m.stampCursor(path, msgs, time.Now())
	}
	return msgs, err
}
