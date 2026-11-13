package transcript

import (
	"bufio"
	"encoding/json"
	"os"
	"strings"
	"time"
)

type cursorTail struct {
	LastUser          string
	PendingTools      bool
	TextOnlyAssistant bool
	LastAssistantText string
}

func cursorScanTail(path string) (cursorTail, error) {
	f, err := os.Open(path)
	if err != nil {
		return cursorTail{}, err
	}
	defer f.Close()
	var out cursorTail
	var lastAssistant map[string]any
	var lastRole string
	br := bufio.NewScanner(f)
	br.Buffer(make([]byte, 0, 64*1024), maxLineBytes)
	for br.Scan() {
		line := strings.TrimSpace(br.Text())
		if line == "" {
			continue
		}
		var row map[string]any
		if json.Unmarshal([]byte(line), &row) != nil {
			continue
		}
		role, _ := row["role"].(string)
		if role != "user" && role != "assistant" {
			continue
		}
		lastRole = role
		msgObj, _ := row["message"].(map[string]any)
		if msgObj == nil {
			continue
		}
		content, _ := msgObj["content"].([]any)
		switch role {
		case "user":
			text := cursorUserText(content)
			if text != "" {
				out.LastUser = wireMsgID(role, text)
			}
		case "assistant":
			lastAssistant = row
		}
	}
	if lastRole == "user" {
		out.PendingTools = true
		return out, br.Err()
	}
	if lastAssistant == nil {
		return out, br.Err()
	}
	msgObj, _ := lastAssistant["message"].(map[string]any)
	content, _ := msgObj["content"].([]any)
	blocks := cursorBlocks(content)
	var textParts []string
	for _, b := range blocks {
		if b.Type == "tool_use" {
			out.PendingTools = true
		}
		if b.Type == "text" {
			if t := cursorAssistantText(b.Text); t != "" {
				textParts = append(textParts, t)
			}
		}
	}
	if len(textParts) > 0 {
		out.LastAssistantText = strings.Join(textParts, "\n\n")
	}
	out.TextOnlyAssistant = !out.PendingTools && out.LastAssistantText != ""
	return out, br.Err()
}

type cursorTrack struct {
	size       int64
	stableAt   time.Time
	lastUser   string
	notified   bool
	lastStatus string
}

func (m *Manager) pollCursorStatus(agentID, path string, size int64, now time.Time) {
	if m.setAgentStatus == nil {
		return
	}
	tail, err := cursorScanTail(path)
	if err != nil {
		return
	}
	m.mu.Lock()
	if m.cursorAgents == nil {
		m.cursorAgents = map[string]*cursorTrack{}
	}
	st := m.cursorAgents[agentID]
	if st == nil {
		st = &cursorTrack{}
		if finishedBeforeWatch(path, now) {
			st.lastUser = tail.LastUser
			st.notified = true
		}
		m.cursorAgents[agentID] = st
	}
	if tail.LastUser != "" && tail.LastUser != st.lastUser {
		st.lastUser = tail.LastUser
		st.notified = false
	}
	if size != st.size {
		st.size = size
		st.stableAt = now
	}
	if tail.LastUser != "" && st.lastUser == "" {
		st.lastUser = tail.LastUser
	}
	onDone := m.onCursorDone
	m.mu.Unlock()
	want := "working"
	if tail.TextOnlyAssistant && !tail.PendingTools && !st.stableAt.IsZero() && now.Sub(st.stableAt) >= 2*time.Second {
		want = "done"
	}
	m.mu.Lock()
	st = m.cursorAgents[agentID]
	prev := st.lastStatus
	if want != st.lastStatus {
		st.lastStatus = want
	}
	notify := want == "done" && !st.notified && onDone != nil
	var body string
	if notify {
		st.notified = true
		body = tail.LastAssistantText
	}
	m.mu.Unlock()
	if notify {
		if prev != want {
			m.setAgentStatus(agentID, want)
		}
		onDone(agentID, body)
		return
	}
	if want != prev {
		m.setAgentStatus(agentID, want)
	}
}

func (m *Manager) SetCursorLifecycle(setStatus func(string, string), onDone func(string, string)) {
	m.mu.Lock()
	m.setAgentStatus = setStatus
	m.onCursorDone = onDone
	m.mu.Unlock()
}

const cursorQuietOnStart = 10 * time.Second

func finishedBeforeWatch(path string, now time.Time) bool {
	info, err := os.Stat(path)
	return err == nil && now.Sub(info.ModTime()) > cursorQuietOnStart
}
