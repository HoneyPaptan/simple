package opencode

import (
	"strings"

	"sh.simple/simpled/internal/wire"
)

func FailedTurn(items []Item) (id, text string, ts int64, ok bool) {
	lastUser := -1
	failAt := -1
	for i, it := range items {
		switch it.Type {
		case "user":
			if strings.TrimSpace(it.Text) != "" {
				lastUser = i
				failAt = -1
			}
		case "assistant":
			if it.Time.Completed > 0 {
				failAt = -1
			}
		case "idle":
			if i < lastUser {
				continue
			}
			if it.Outcome == "failed" {
				if msg := errorText(it.Error); msg != "" {
					failAt = i
				}
				continue
			}
			failAt = -1
		}
	}
	if failAt < 0 {
		return "", "", 0, false
	}
	it := items[failAt]
	return it.ID, errorText(it.Error), it.Time.Created, true
}

func FailedIDs(items []Item) []string {
	var ids []string
	for _, it := range items {
		if it.Type == "idle" && it.Outcome == "failed" && it.ID != "" && errorText(it.Error) != "" {
			ids = append(ids, it.ID)
		}
	}
	return ids
}

func MessageFromEvent(root map[string]any) string {
	if root == nil {
		return ""
	}
	if props, ok := root["properties"].(map[string]any); ok {
		if msg := errorNode(props["error"]); msg != "" {
			return msg
		}
	}
	if msg := errorNode(root["error"]); msg != "" {
		return msg
	}
	for _, key := range []string{"message", "reason"} {
		if s, ok := root[key].(string); ok && strings.TrimSpace(s) != "" {
			return strings.TrimSpace(s)
		}
	}
	return ""
}

func errorNode(raw any) string {
	switch v := raw.(type) {
	case string:
		return strings.TrimSpace(v)
	case map[string]any:
		if s, ok := v["message"].(string); ok && strings.TrimSpace(s) != "" {
			return strings.TrimSpace(s)
		}
		if data, ok := v["data"].(map[string]any); ok {
			if s, ok := data["message"].(string); ok && strings.TrimSpace(s) != "" {
				return strings.TrimSpace(s)
			}
		}
	}
	return ""
}

func OmitFailureEcho(msgs []wire.Message, itemID, failureText string) []wire.Message {
	want := stoppedPrefix + strings.TrimSpace(failureText)
	out := make([]wire.Message, 0, len(msgs))
	for _, m := range msgs {
		if m.Role == "assistant" && m.Text != nil {
			text := strings.TrimSpace(*m.Text)
			if (itemID != "" && m.ID == itemID) || (want != stoppedPrefix && text == want) {
				continue
			}
		}
		out = append(out, m)
	}
	return out
}

func BlankAssistant(id string, ts int64) wire.Message {
	blank := ""
	return wire.Message{ID: id, Role: "assistant", Ts: ts, Text: &blank}
}
