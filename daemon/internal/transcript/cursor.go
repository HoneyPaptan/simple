package transcript

import (
	"bufio"
	"encoding/json"
	"os"
	"path/filepath"
	"regexp"
	"strconv"
	"strings"

	"sh.simple/simpled/internal/wire"
)

var cursorTimestampTag = regexp.MustCompile(`(?s)<timestamp>.*?</timestamp>\s*`)

func ParseCursorJSONL(path string) ([]wire.Message, error) {
	f, err := os.Open(path)
	if err != nil {
		return nil, err
	}
	defer f.Close()
	var messages []wire.Message
	br := bufio.NewScanner(f)
	br.Buffer(make([]byte, 0, 64*1024), maxLineBytes)
	base := cursorChatStartMs(path)
	var idx int
	toolUses := map[string]*wire.Tool{}
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
		msgObj, _ := row["message"].(map[string]any)
		if msgObj == nil {
			continue
		}
		ts := base + int64(idx)
		if v, ok := row["timestamp"].(float64); ok {
			ts = int64(v)
		}
		idBase, _ := row["id"].(string)
		content, _ := msgObj["content"].([]any)
		switch role {
		case "user":
			text := cursorUserText(content)
			if text == "" || isCursorInternalPrompt(text) {
				continue
			}
			id := idBase
			if id == "" {
				id = wireMsgID(role, text)
			}
			messages = append(messages, wire.Message{ID: id, Role: role, Ts: ts, Text: &text})
			idx++
		case "assistant":
			blocks := cursorBlocks(content)
			for i, b := range blocks {
				if b.Type == "text" {
					t := cursorAssistantText(b.Text)
					if t == "" {
						continue
					}
					id := idBase
					if id == "" {
						id = wireMsgID(role, t)
					} else {
						id = id + ":t" + strconv.Itoa(i)
					}
					messages = append(messages, wire.Message{ID: id, Role: "assistant", Ts: ts, Text: &t})
				}
				if b.Type == "tool_use" {
					tool := toolFromUse(b)
					if b.ID != "" {
						toolUses[b.ID] = &tool
					}
					tid := b.ID
					if tid == "" {
						tid = wireMsgID("tool", tool.Name+tool.Summary)
					}
					messages = append(messages, wire.Message{ID: tid, Role: "tool", Ts: ts, Tool: &tool})
				}
			}
			idx++
		}
	}
	return messages, br.Err()
}

func cursorBlocks(content []any) []contentBlock {
	var blocks []contentBlock
	for _, raw := range content {
		block, _ := raw.(map[string]any)
		if block == nil {
			continue
		}
		typ, _ := block["type"].(string)
		switch typ {
		case "text":
			t, _ := block["text"].(string)
			blocks = append(blocks, contentBlock{Type: "text", Text: t})
		case "tool_use":
			b := contentBlock{Type: "tool_use"}
			b.Name, _ = block["name"].(string)
			if id, ok := block["id"].(string); ok {
				b.ID = id
			}
			if in, ok := block["input"]; ok {
				b.Input, _ = json.Marshal(in)
			}
			blocks = append(blocks, b)
		}
	}
	return blocks
}

const cursorTaskResultPrompt = "Briefly inform the user about the task result"

func isCursorInternalPrompt(text string) bool {
	return strings.HasPrefix(text, cursorTaskResultPrompt)
}

func cursorUserText(content []any) string {
	for _, raw := range content {
		block, _ := raw.(map[string]any)
		if block == nil {
			continue
		}
		typ, _ := block["type"].(string)
		if typ != "text" {
			continue
		}
		t, _ := block["text"].(string)
		if t == "" {
			continue
		}
		t = cursorTimestampTag.ReplaceAllString(t, "")
		t = strings.TrimSpace(t)
		if strings.HasPrefix(t, "<user_query>") {
			t = strings.TrimPrefix(t, "<user_query>")
			t = strings.TrimSuffix(t, "</user_query>")
			t = strings.TrimSpace(t)
		}
		return t
	}
	return ""
}

func cursorAssistantText(s string) string {
	var parts []string
	for _, p := range strings.Split(s, "\n\n") {
		p = strings.TrimSpace(p)
		if p == "" || p == "[REDACTED]" {
			continue
		}
		parts = append(parts, p)
	}
	return strings.Join(parts, "\n\n")
}

func cursorChatStartMs(path string) int64 {
	st, err := os.Stat(filepath.Dir(path))
	if err != nil {
		return 0
	}
	return st.ModTime().UnixMilli()
}
