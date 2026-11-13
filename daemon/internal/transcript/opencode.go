package transcript

import (
	"encoding/json"
	"sync"

	"sh.simple/simpled/internal/wire"
)

func (m *Manager) AppendOpenCode(terminalID string, body json.RawMessage) {
	var root map[string]any
	if json.Unmarshal(body, &root) != nil {
		return
	}
	msg, ok := parseOpenCodeMessage(root)
	if !ok {
		return
	}
	m.mu.Lock()
	meta := m.sessions[terminalID]
	m.mu.Unlock()
	if meta == nil {
		return
	}
	m.pub("transcript.append", wire.TranscriptAppend{ID: terminalID, Messages: []wire.Message{msg}})
}

var opencodeMu sync.Mutex
var opencodeSeen = map[string]bool{}

func parseOpenCodeMessage(root map[string]any) (wire.Message, bool) {
	id, _ := root["id"].(string)
	if id == "" {
		id, _ = root["messageID"].(string)
	}
	if id == "" {
		return wire.Message{}, false
	}
	opencodeMu.Lock()
	key := id
	if opencodeSeen[key] {
		opencodeMu.Unlock()
		return wire.Message{}, false
	}
	opencodeSeen[key] = true
	opencodeMu.Unlock()
	role, _ := root["role"].(string)
	if role == "" {
		role = "assistant"
	}
	text := extractOpenCodeText(root)
	if text == "" {
		return wire.Message{}, false
	}
	ts := int64(0)
	if v, ok := root["time"].(map[string]any); ok {
		if created, ok := v["created"].(float64); ok {
			ts = int64(created)
		}
	}
	return wire.Message{ID: id, Role: role, Ts: ts, Text: &text}, true
}

func extractOpenCodeText(root map[string]any) string {
	if p, ok := root["part"].(map[string]any); ok {
		if t, ok := p["text"].(string); ok {
			return t
		}
	}
	if parts, ok := root["parts"].([]any); ok {
		for _, raw := range parts {
			p, _ := raw.(map[string]any)
			if t, ok := p["text"].(string); ok && t != "" {
				return t
			}
		}
	}
	if t, ok := root["text"].(string); ok {
		return t
	}
	return ""
}
