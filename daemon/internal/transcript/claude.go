package transcript

import (
	"bufio"
	"encoding/json"
	"fmt"
	"os"
	"sort"
	"strconv"
	"strings"
	"time"

	"sh.simple/simpled/internal/wire"
)

const maxToolOutput = 4096

type claudeLine struct {
	Type      string          `json:"type"`
	UUID      string          `json:"uuid"`
	Timestamp string          `json:"timestamp"`
	IsMeta    bool            `json:"isMeta"`
	Message   json.RawMessage `json:"message"`
	Subtype   string          `json:"subtype"`
	Content   json.RawMessage `json:"content"`
	Denial    string          `json:"toolDenialKind"`
}

type claudeMessage struct {
	Role    string          `json:"role"`
	Content json.RawMessage `json:"content"`
}

type contentBlock struct {
	Type      string          `json:"type"`
	Text      string          `json:"text"`
	ID        string          `json:"id"`
	Name      string          `json:"name"`
	Input     json.RawMessage `json:"input"`
	ToolUseID string          `json:"tool_use_id"`
	Content   json.RawMessage `json:"content"`
	IsError   bool            `json:"is_error"`
	Thinking  string          `json:"thinking"`
}

func ParseClaudeJSONL(path string) ([]wire.Message, error) {
	f, err := os.Open(path)
	if err != nil {
		return nil, err
	}
	defer f.Close()
	var messages []wire.Message
	toolUses := map[string]*wire.Tool{}
	br := bufio.NewScanner(f)
	br.Buffer(make([]byte, 0, 64*1024), maxLineBytes)
	for br.Scan() {
		line := strings.TrimSpace(br.Text())
		if line == "" {
			continue
		}
		var row claudeLine
		if json.Unmarshal([]byte(line), &row) != nil {
			continue
		}
		if row.IsMeta || row.Type == "mode" || row.Type == "file-history-snapshot" {
			continue
		}
		if row.Type == "system" && row.Subtype == "local_command" {
			if m, ok := localCommandMessage(row); ok {
				messages = append(messages, m)
			}
			continue
		}
		if row.Type != "user" && row.Type != "assistant" {
			continue
		}
		var msg claudeMessage
		if json.Unmarshal(row.Message, &msg) != nil {
			continue
		}
		attachToolResults(toolUses, msg.Content, row.Denial != "")
		ts := parseTS(row.Timestamp)
		id := row.UUID
		if id == "" {
			continue
		}
		switch msg.Role {
		case "user":
			text := extractText(msg.Content)
			if cmd := slashCommandText(text); cmd != "" {
				text = cmd
			} else if text == "" || strings.HasPrefix(text, "<") {
				continue
			}
			if strings.HasPrefix(text, "[Request interrupted by user") {
				messages = append(messages, eventMessage(id, ts, "Stopped"))
				continue
			}
			t := text
			messages = append(messages, wire.Message{ID: id, Role: "user", Ts: ts, Text: &t})
		case "assistant":
			blocks := parseBlocks(msg.Content)
			for i, b := range blocks {
				if b.Type == "thinking" && strings.TrimSpace(b.Thinking) != "" {
					t := strings.TrimSpace(b.Thinking)
					messages = append(messages, wire.Message{ID: id + ":r" + strconv.Itoa(i), Role: "thinking", Ts: ts, Text: &t})
				}
				if b.Type == "text" && strings.TrimSpace(b.Text) != "" {
					t := b.Text
					messages = append(messages, wire.Message{ID: id + ":t", Role: "assistant", Ts: ts, Text: &t})
				}
				if b.Type == "tool_use" {
					tool := toolFromUse(b)
					toolUses[b.ID] = &tool
					messages = append(messages, wire.Message{ID: b.ID, Role: "tool", Ts: ts, Tool: &tool})
				}
			}
		}
	}
	return inFileOrder(messages), br.Err()
}

func inFileOrder(messages []wire.Message) []wire.Message {
	for i := 1; i < len(messages); i++ {
		if messages[i].Ts <= messages[i-1].Ts {
			messages[i].Ts = messages[i-1].Ts + 1
		}
	}
	return messages
}

func eventMessage(id string, ts int64, text string) wire.Message {
	return wire.Message{ID: id, Role: "event", Ts: ts, Text: &text}
}

func attachToolResults(toolUses map[string]*wire.Tool, content json.RawMessage, denied bool) {
	for _, b := range parseBlocks(content) {
		if b.Type != "tool_result" || b.ToolUseID == "" {
			continue
		}
		if t, ok := toolUses[b.ToolUseID]; ok {
			out := trimOutput(extractToolResult(b.Content))
			t.Output = &out
			t.Status = toolResultStatus(b.IsError)
			if denied && b.IsError {
				t.Status = "denied"
			}
		}
	}
}

func parseBlocks(raw json.RawMessage) []contentBlock {
	if len(raw) == 0 {
		return nil
	}
	if raw[0] == '"' {
		return nil
	}
	if raw[0] == '{' {
		var one contentBlock
		if json.Unmarshal(raw, &one) == nil {
			return []contentBlock{one}
		}
		return nil
	}
	var blocks []contentBlock
	_ = json.Unmarshal(raw, &blocks)
	return blocks
}

func extractText(raw json.RawMessage) string {
	if len(raw) == 0 {
		return ""
	}
	if raw[0] == '"' {
		var s string
		_ = json.Unmarshal(raw, &s)
		return s
	}
	var blocks []contentBlock
	if json.Unmarshal(raw, &blocks) != nil {
		return ""
	}
	var parts []string
	for _, b := range blocks {
		if b.Type == "text" {
			parts = append(parts, b.Text)
		}
	}
	return strings.Join(parts, "\n")
}

func toolFromUse(b contentBlock) wire.Tool {
	summary := toolSummary(b.Name, b.Input)
	in := string(b.Input)
	if len(in) > maxToolOutput {
		in = in[:maxToolOutput]
	}
	diff := toolDiff(b.Name, b.Input)
	status := "running"
	return wire.Tool{
		Name:    b.Name,
		Summary: summary,
		Input:   &in,
		Status:  status,
		Diff:    diff,
	}
}

func toolSummary(name string, input json.RawMessage) string {
	var m map[string]any
	_ = json.Unmarshal(input, &m)
	str := func(key string) string {
		v, _ := m[key].(string)
		return v
	}
	switch name {
	case "Bash":
		if c := str("description"); c != "" {
			return oneLine(c)
		}
		if c := str("command"); c != "" {
			return oneLine(c)
		}
	case "Read", "Edit", "Write", "MultiEdit", "NotebookEdit":
		if p := str("path"); p != "" {
			return filepathBase(p)
		}
		if p := str("file_path"); p != "" {
			return filepathBase(p)
		}
		if p := str("notebook_path"); p != "" {
			return filepathBase(p)
		}
	case "Shell":
		if c := str("command"); c != "" {
			return oneLine(c)
		}
	case "Grep", "Glob":
		if p := str("pattern"); p != "" {
			return oneLine(p)
		}
	case "WebFetch":
		return oneLine(str("url"))
	case "WebSearch":
		return oneLine(str("query"))
	case "Task", "Agent":
		if d := str("description"); d != "" {
			return oneLine(d)
		}
	case "TodoWrite":
		if todos, ok := m["todos"].([]any); ok {
			return todoSummary(todos)
		}
	}
	return name
}

func todoSummary(todos []any) string {
	done := 0
	for _, t := range todos {
		if item, ok := t.(map[string]any); ok && item["status"] == "completed" {
			done++
		}
	}
	return fmt.Sprintf("%d of %d done", done, len(todos))
}

func toolResultStatus(isError bool) string {
	if isError {
		return "error"
	}
	return "ok"
}

func toolDiff(name string, input json.RawMessage) *string {
	if name != "Edit" && name != "Write" && name != "MultiEdit" {
		return nil
	}
	var m map[string]any
	if json.Unmarshal(input, &m) != nil {
		return nil
	}
	oldS, _ := m["old_string"].(string)
	newS, _ := m["new_string"].(string)
	if oldS == "" && newS == "" {
		if content, ok := m["content"].(string); ok {
			newS = content
		}
	}
	if oldS == "" && newS == "" {
		return nil
	}
	diff := unifiedDiff(oldS, newS)
	if diff == "" {
		return nil
	}
	return &diff
}

func unifiedDiff(oldS, newS string) string {
	return "---\n+++ \n-" + strings.ReplaceAll(oldS, "\n", "\n-") + "\n+" + strings.ReplaceAll(newS, "\n", "\n+")
}

func extractToolResult(raw json.RawMessage) string {
	if len(raw) == 0 {
		return ""
	}
	if raw[0] == '"' {
		var s string
		_ = json.Unmarshal(raw, &s)
		return s
	}
	if raw[0] == '[' {
		return extractText(raw)
	}
	return string(raw)
}

func trimOutput(s string) string {
	if len(s) <= maxToolOutput {
		return s
	}
	return s[:maxToolOutput]
}

func oneLine(s string) string {
	s = strings.TrimSpace(s)
	if idx := strings.IndexByte(s, '\n'); idx >= 0 {
		s = s[:idx]
	}
	if len(s) > 80 {
		s = s[:80]
	}
	return s
}

func filepathBase(p string) string {
	p = strings.ReplaceAll(p, "\\", "/")
	if i := strings.LastIndex(p, "/"); i >= 0 {
		return p[i+1:]
	}
	return p
}

func parseTS(s string) int64 {
	if s == "" {
		return time.Now().UnixMilli()
	}
	t, err := time.Parse(time.RFC3339Nano, s)
	if err != nil {
		return time.Now().UnixMilli()
	}
	return t.UnixMilli()
}

func PageMessages(all []wire.Message, before *string, limit int) ([]wire.Message, *string) {
	if limit <= 0 {
		limit = 50
	}
	sort.SliceStable(all, func(i, j int) bool { return all[i].Ts < all[j].Ts })
	end := len(all)
	if before != nil {
		for i := range all {
			if all[i].ID == *before {
				end = i
				break
			}
		}
	}
	start := end - limit
	if start < 0 {
		start = 0
	}
	out := all[start:end]
	var next *string
	if start > 0 {
		id := all[start].ID
		next = &id
	}
	return out, next
}
