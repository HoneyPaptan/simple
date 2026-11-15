package hooks

import (
	"context"
	"encoding/json"
	"strings"
	"time"

	"sh.simple/simpled/internal/agents"
	"sh.simple/simpled/internal/inbox"
	"sh.simple/simpled/internal/wire"
)

type cursorInput struct {
	ConversationID string          `json:"conversation_id"`
	TranscriptPath string          `json:"transcript_path"`
	SessionID      string          `json:"session_id"`
	Command        string          `json:"command"`
	ToolName       string          `json:"tool_name"`
	ToolInput      json.RawMessage `json:"tool_input"`
	McpServerName  string          `json:"mcp_server_name"`
	Prompt         string          `json:"prompt"`
	Text           string          `json:"text"`
	Status         string          `json:"status"`
	Reason         string          `json:"reason"`
}

func (d *Daemon) handleCursor(ctx context.Context, terminalID, event string, body json.RawMessage) (json.RawMessage, bool) {
	var in cursorInput
	_ = json.Unmarshal(body, &in)
	switch event {
	case "sessionStart":
		sid := in.SessionID
		if sid == "" {
			sid = in.ConversationID
		}
		if sid != "" {
			d.Transcripts.BindSession(terminalID, sid, "", in.TranscriptPath, "cursor")
		}
		return json.RawMessage(`{}`), true
	case "beforeShellExecution", "beforeMCPExecution":
		return d.cursorPermission(ctx, terminalID, event, in)
	case "beforeSubmitPrompt":
		d.Agents.SetAgentFields(terminalID, "working", nil)
		return json.RawMessage(`{"continue":true}`), true
	case "afterAgentResponse":
		return json.RawMessage(`{}`), true
	case "stop":
		d.Agents.SetAgentFields(terminalID, "done", nil)
		id, _ := inbox.NewItemID()
		bodyText := in.Text
		item := wire.Item{ID: id, Agent: terminalID, Kind: "done", Title: "Finished", Body: &bodyText, CreatedMs: inbox.NowMs()}
		_ = d.Inbox.Add(item)
		return json.RawMessage(`{}`), true
	case "sessionEnd":
		d.Agents.SetAgentFields(terminalID, "done", nil)
		d.Inbox.ResolveForAgent(terminalID)
		return json.RawMessage(`{}`), true
	default:
		return json.RawMessage(`{}`), true
	}
}

func (d *Daemon) cursorPermission(ctx context.Context, terminalID, event string, in cursorInput) (json.RawMessage, bool) {
	mode := simpleModeFromAgent(terminalID, d.Agents)
	if mode == "full" {
		return json.RawMessage(`{"permission":"allow"}`), true
	}
	id, _ := inbox.NewItemID()
	title, toolLabel, cmd := cursorApprovalFields(event, in)
	item := wire.Item{
		ID: id, Agent: terminalID, Kind: "approval", Title: title,
		Command: &cmd, CreatedMs: inbox.NowMs(),
	}
	if toolLabel != "" {
		item.Tool = &toolLabel
	}
	_ = d.Inbox.Add(item)
	allow, known := d.waitAnswer(ctx, id, 580*time.Second).verdict()
	switch {
	case !known:
		return json.RawMessage(`{"permission":"ask"}`), true
	case !allow:
		return json.RawMessage(`{"permission":"deny"}`), true
	}
	return json.RawMessage(`{"permission":"allow"}`), true
}

func (d *Daemon) handleOpenCode(ctx context.Context, terminalID, event string, body json.RawMessage) (json.RawMessage, bool) {
	var root map[string]any
	_ = json.Unmarshal(body, &root)
	switch event {
	case "session.created":
		sid, _ := root["sessionID"].(string)
		if sid == "" {
			sid, _ = root["session_id"].(string)
		}
		if sid != "" {
			d.Transcripts.BindSession(terminalID, sid, "", "", "opencode")
		}
		return json.RawMessage(`{}`), true
	case "session.status", "session.idle", "session.error":
		st, _ := root["status"].(string)
		if st == "idle" {
			d.Agents.SetAgentFields(terminalID, "idle", nil)
		} else if st == "busy" || st == "running" {
			d.Agents.SetAgentFields(terminalID, "working", nil)
		} else if event == "session.error" {
			if a, ok := d.Agents.Get(terminalID); !ok || a.Status != "working" {
				d.Agents.LatchSendFailure(terminalID, openCodeFailText(root))
			}
		}
		return json.RawMessage(`{}`), true
	case "permission.ask":
		return d.opencodePermissionHook(ctx, terminalID, root)
	case "permission.asked":
		return d.opencodePermissionEvent(ctx, terminalID, root)
	case "message.updated", "message.part.updated":
		d.Transcripts.AppendOpenCode(terminalID, body)
		return json.RawMessage(`{}`), true
	default:
		return json.RawMessage(`{}`), true
	}
}

func openCodeFailText(root map[string]any) string {
	for _, key := range []string{"error", "message", "reason"} {
		raw, ok := root[key]
		if !ok {
			continue
		}
		if s, ok := raw.(string); ok && strings.TrimSpace(s) != "" {
			return strings.TrimSpace(s)
		}
	}
	return agents.CouldNotSend()
}

func opencodePermissionFields(root map[string]any) (title, body string) {
	title = "Permission"
	if props, ok := root["properties"].(map[string]any); ok {
		if perm, ok := props["permission"].(string); ok && perm != "" {
			title = perm
		}
		if meta, ok := props["metadata"].(map[string]any); ok {
			if msg, ok := meta["message"].(string); ok {
				body = msg
			}
		}
	}
	if t, ok := root["title"].(string); ok && t != "" {
		title = t
	}
	if typ, ok := root["type"].(string); ok && title == "Permission" && typ != "" {
		title = typ
	}
	if body == "" {
		if meta, ok := root["metadata"].(map[string]any); ok {
			if msg, ok := meta["message"].(string); ok {
				body = msg
			}
		}
	}
	if body == "" {
		if msg, ok := root["message"].(string); ok {
			body = msg
		}
	}
	return title, body
}

func opencodePermissionID(root map[string]any) string {
	if id, ok := root["id"].(string); ok {
		return id
	}
	props, _ := root["properties"].(map[string]any)
	id, _ := props["id"].(string)
	return id
}

func (d *Daemon) opencodePermissionWait(ctx context.Context, terminalID string, root map[string]any, owner bool) *inboxAnswer {
	permID := opencodePermissionID(root)
	d.mu.Lock()
	if d.ocAsks == nil {
		d.ocAsks = map[string]string{}
	}
	itemID, seen := d.ocAsks[permID]
	if permID != "" && seen && !owner {
		d.mu.Unlock()
		return nil
	}
	if !seen {
		itemID, _ = inbox.NewItemID()
		if permID != "" {
			d.ocAsks[permID] = itemID
		}
	}
	d.mu.Unlock()
	if !seen {
		title, body := opencodePermissionFields(root)
		item := wire.Item{ID: itemID, Agent: terminalID, Kind: "approval", Title: title, CreatedMs: inbox.NowMs()}
		if body != "" {
			item.Body = &body
		}
		_ = d.Inbox.Add(item)
	}
	ans := d.waitAnswer(ctx, itemID, 580*time.Second)
	if !seen && permID != "" {
		d.mu.Lock()
		delete(d.ocAsks, permID)
		d.mu.Unlock()
	}
	return ans
}

func (d *Daemon) opencodePermissionHook(ctx context.Context, terminalID string, root map[string]any) (json.RawMessage, bool) {
	allow, known := d.opencodePermissionWait(ctx, terminalID, root, true).verdict()
	switch {
	case !known:
		return json.RawMessage(`{}`), true
	case !allow:
		return json.RawMessage(`{"status":"deny"}`), true
	}
	return json.RawMessage(`{"status":"allow"}`), true
}

func (d *Daemon) opencodePermissionEvent(ctx context.Context, terminalID string, root map[string]any) (json.RawMessage, bool) {
	ans := d.opencodePermissionWait(ctx, terminalID, root, false)
	allow, known := ans.verdict()
	switch {
	case !known:
		return json.RawMessage(`{}`), true
	case !allow:
		return json.RawMessage(`{"reply":"reject"}`), true
	case ans.decision == "always":
		return json.RawMessage(`{"reply":"always"}`), true
	}
	return json.RawMessage(`{"reply":"once"}`), true
}

func cursorApprovalFields(event string, in cursorInput) (title, toolLabel, cmd string) {
	cmd = in.Command
	if cmd == "" {
		cmd = string(in.ToolInput)
	}
	if event == "beforeMCPExecution" {
		toolLabel = in.ToolName
		if d := toolDescription(in.ToolInput); d != "" {
			title = d
		} else if in.McpServerName != "" && in.ToolName != "" {
			title = in.McpServerName + ": " + in.ToolName
		} else {
			title = in.ToolName
		}
		if strings.EqualFold(toolLabel, title) {
			toolLabel = ""
		}
		return title, toolLabel, cmd
	}
	if d := toolDescription(in.ToolInput); d != "" {
		title = d
		toolLabel = "Shell"
		if strings.EqualFold(toolLabel, title) {
			toolLabel = ""
		}
		return title, toolLabel, cmd
	}
	if in.Command != "" {
		title = in.Command
		return title, "", cmd
	}
	title = "Shell command"
	return title, "", cmd
}
