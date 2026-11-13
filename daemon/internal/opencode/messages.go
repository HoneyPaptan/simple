package opencode

import (
	"encoding/json"
	"strconv"
	"strings"

	"sh.simple/simpled/internal/wire"
)

const maxToolText = 4096

type Item struct {
	ID      string          `json:"id"`
	Type    string          `json:"type"`
	Text    string          `json:"text"`
	Time    Times           `json:"time"`
	Content []Block         `json:"content"`
	Outcome string          `json:"outcome"`
	Error   json.RawMessage `json:"error"`
	Model   *ModelRef       `json:"model,omitempty"`
	Tokens  json.RawMessage `json:"tokens,omitempty"`
}

type Times struct {
	Created   int64 `json:"created"`
	Completed int64 `json:"completed"`
}

type Block struct {
	Type  string    `json:"type"`
	ID    string    `json:"id"`
	Name  string    `json:"name"`
	Text  string    `json:"text"`
	State toolState `json:"state"`
}

type toolState struct {
	Status   string          `json:"status"`
	Input    json.RawMessage `json:"input"`
	Content  []Block         `json:"content"`
	Error    json.RawMessage `json:"error"`
	Metadata struct {
		Files []struct {
			Patch string `json:"patch"`
		} `json:"files"`
	} `json:"metadata"`
}

func ToWire(items []Item) []wire.Message {
	var out []wire.Message
	for _, it := range items {
		switch it.Type {
		case "user":
			if strings.TrimSpace(it.Text) != "" {
				out = append(out, textMessage(it.ID, "user", it.Time.Created, it.Text))
			}
		case "assistant":
			out = append(out, assistantMessages(it)...)
		case "idle":
			if it.Outcome != "" && it.Outcome != "succeeded" && it.Outcome != "failed" {
				out = append(out, textMessage(it.ID, "event", it.Time.Created, "Stopped"))
			}
			if msg := errorText(it.Error); it.Outcome == "failed" && msg != "" {
				out = append(out, textMessage(it.ID, "assistant", it.Time.Created, stoppedPrefix+msg))
			}
		}
	}
	return out
}

func Busy(items []Item) bool {
	for i := len(items) - 1; i >= 0; i-- {
		switch items[i].Type {
		case "idle":
			return false
		case "assistant":
			return items[i].Time.Completed == 0
		case "user":
			return true
		}
	}
	return false
}

func assistantMessages(it Item) []wire.Message {
	var out []wire.Message
	for i, b := range it.Content {
		switch b.Type {
		case "reasoning":
			if strings.TrimSpace(b.Text) != "" {
				out = append(out, textMessage(it.ID+":"+strconv.Itoa(i), "thinking", it.Time.Created, strings.TrimSpace(b.Text)))
			}
		case "text":
			if strings.TrimSpace(b.Text) != "" {
				out = append(out, textMessage(it.ID+":"+strconv.Itoa(i), "assistant", it.Time.Created, b.Text))
			}
		case "tool":
			tool := toolFromBlock(b)
			id := b.ID
			if id == "" {
				id = it.ID + ":" + strconv.Itoa(i)
			}
			out = append(out, wire.Message{ID: id, Role: "tool", Ts: it.Time.Created, Tool: &tool})
		}
	}
	if n := len(out); n > 0 && it.Time.Completed > out[n-1].Ts {
		out[n-1].Ts = it.Time.Completed
	}
	return out
}

func textMessage(id, role string, ts int64, text string) wire.Message {
	t := text
	return wire.Message{ID: id, Role: role, Ts: ts, Text: &t}
}

func toolFromBlock(b Block) wire.Tool {
	in := clip(string(b.State.Input))
	out := clip(toolOutput(b.State))
	tool := wire.Tool{Name: b.Name, Summary: toolSummary(b.State.Input, b.Name), Input: &in, Output: &out, Status: toolStatus(b.State.Status)}
	if diff := toolDiff(b.State); diff != "" {
		tool.Diff = &diff
	}
	return tool
}

func toolStatus(s string) string {
	switch s {
	case "completed":
		return "ok"
	case "error":
		return "error"
	}
	return "running"
}

func toolOutput(s toolState) string {
	if msg := errorText(s.Error); msg != "" {
		return msg
	}
	var parts []string
	for _, c := range s.Content {
		if c.Type == "text" && c.Text != "" {
			parts = append(parts, c.Text)
		}
	}
	return strings.Join(parts, "\n")
}

func errorText(raw json.RawMessage) string {
	if len(raw) == 0 || string(raw) == "null" {
		return ""
	}
	var text string
	if json.Unmarshal(raw, &text) == nil {
		return text
	}
	var obj struct {
		Message string `json:"message"`
	}
	if json.Unmarshal(raw, &obj) == nil && obj.Message != "" {
		return obj.Message
	}
	return string(raw)
}

func toolDiff(s toolState) string {
	var parts []string
	for _, f := range s.Metadata.Files {
		if f.Patch != "" {
			parts = append(parts, f.Patch)
		}
	}
	return clip(strings.Join(parts, "\n"))
}

func toolSummary(input json.RawMessage, name string) string {
	var m map[string]any
	_ = json.Unmarshal(input, &m)
	for _, key := range []string{"description", "filePath", "path", "pattern", "query", "url", "command"} {
		if v, ok := m[key].(string); ok && strings.TrimSpace(v) != "" {
			if key == "filePath" || key == "path" {
				return baseName(v)
			}
			return firstLine(v)
		}
	}
	return name
}

func baseName(p string) string {
	if i := strings.LastIndex(p, "/"); i >= 0 && i < len(p)-1 {
		return p[i+1:]
	}
	return p
}

func firstLine(s string) string {
	s = strings.TrimSpace(s)
	if i := strings.IndexByte(s, '\n'); i >= 0 {
		s = s[:i]
	}
	if len(s) > 80 {
		s = s[:80]
	}
	return s
}

func clip(s string) string {
	if len(s) > maxToolText {
		return s[:maxToolText]
	}
	return s
}

const stoppedPrefix = "OpenCode stopped: "
