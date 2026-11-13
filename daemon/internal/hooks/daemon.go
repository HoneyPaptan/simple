package hooks

import (
	"context"
	"encoding/json"
	"fmt"
	"strconv"
	"strings"
	"sync"
	"time"

	"sh.simple/simpled/internal/agents"
	"sh.simple/simpled/internal/inbox"
	"sh.simple/simpled/internal/transcript"
	"sh.simple/simpled/internal/wire"
)

type Daemon struct {
	Inbox       *inbox.Store
	Agents      *agents.Store
	Transcripts *transcript.Manager
	SimpledPath string

	mu      sync.Mutex
	pending map[string][]chan inboxAnswer
	ocAsks  map[string]string
}

type inboxAnswer struct {
	decision string
	option   *string
	text     *string
}

func (a *inboxAnswer) verdict() (allow, known bool) {
	if a == nil {
		return false, false
	}
	switch a.decision {
	case "allow", "always":
		return true, true
	case "deny":
		return false, true
	}
	return false, false
}

type HookInput struct {
	SessionID      string          `json:"session_id"`
	TranscriptPath string          `json:"transcript_path"`
	Cwd            string          `json:"cwd"`
	PermissionMode string          `json:"permission_mode"`
	HookEventName  string          `json:"hook_event_name"`
	ToolName       string          `json:"tool_name"`
	ToolInput      json.RawMessage `json:"tool_input"`
	Notification   *notificationIn `json:"notification,omitempty"`
	StopReason     string          `json:"stop_reason,omitempty"`
	LastAssistant  string          `json:"last_assistant_message,omitempty"`
}

type notificationIn struct {
	Type string `json:"type"`
}

func New(inbox *inbox.Store, agents *agents.Store, transcripts *transcript.Manager, simpledPath string) *Daemon {
	return &Daemon{
		Inbox:       inbox,
		Agents:      agents,
		Transcripts: transcripts,
		SimpledPath: simpledPath,
		pending:     make(map[string][]chan inboxAnswer),
	}
}

func (d *Daemon) KnowsTerminal(terminalID string) bool {
	_, ok := d.Agents.Get(terminalID)
	return ok
}

func (d *Daemon) Handle(ctx context.Context, kind, terminalID, event string, body json.RawMessage) (json.RawMessage, bool) {
	var in HookInput
	_ = json.Unmarshal(body, &in)
	in.HookEventName = event
	if kind == "cursor" {
		return d.handleCursor(ctx, terminalID, event, body)
	}
	if kind == "opencode" {
		return d.handleOpenCode(ctx, terminalID, event, body)
	}
	switch event {
	case "SessionStart":
		if in.SessionID != "" {
			d.Transcripts.BindSession(terminalID, in.SessionID, in.Cwd, in.TranscriptPath, kind)
		}
		return json.RawMessage(`{}`), true
	case "PermissionRequest":
		return d.permissionRequest(ctx, kind, terminalID, in)
	case "PreToolUse":
		if in.ToolName == "AskUserQuestion" {
			return d.askQuestion(ctx, terminalID, in)
		}
		return json.RawMessage(`{}`), true
	case "Notification":
		return d.notification(terminalID, in)
	case "Stop":
		return d.stopHook(terminalID, in)
	case "UserPromptSubmit":
		d.Agents.SetAgentFields(terminalID, "working", nil)
		return json.RawMessage(`{}`), true
	case "SessionEnd":
		d.Agents.SetAgentFields(terminalID, "done", nil)
		d.Inbox.ResolveForAgent(terminalID)
		return json.RawMessage(`{}`), true
	default:
		return json.RawMessage(`{}`), true
	}
}

func (d *Daemon) permissionRequest(ctx context.Context, kind, terminalID string, in HookInput) (json.RawMessage, bool) {
	mode := simpleModeFromAgent(terminalID, d.Agents)
	if mode == "full" {
		return allowPermission(), true
	}
	if mode == "auto" && isFileEdit(in.ToolName) {
		return allowPermission(), true
	}
	id, _ := inbox.NewItemID()
	title := approvalTitle(in.ToolName, in.ToolInput)
	cmd := toolCommand(in.ToolName, in.ToolInput)
	var toolPtr *string
	if sub := approvalToolSubtitle(in.ToolName, title); sub != "" {
		toolPtr = &sub
	}
	item := wire.Item{
		ID: id, Agent: terminalID, Kind: "approval", Title: title,
		Command: &cmd, Tool: toolPtr, CreatedMs: inbox.NowMs(),
	}
	_ = d.Inbox.Add(item)
	timeout := 580 * time.Second
	if kind == "codex" {
		timeout = 590 * time.Second
	}
	allow, known := d.waitAnswer(ctx, id, timeout).verdict()
	if !known {
		return nil, false
	}
	if !allow {
		return denyPermission(), true
	}
	return allowPermission(), true
}

func (d *Daemon) askQuestion(ctx context.Context, terminalID string, in HookInput) (json.RawMessage, bool) {
	var input struct {
		Questions []struct {
			Question string `json:"question"`
			Header   string `json:"header"`
			Options  []struct {
				Label string `json:"label"`
			} `json:"options"`
			MultiSelect bool `json:"multiSelect"`
		} `json:"questions"`
	}
	_ = json.Unmarshal(in.ToolInput, &input)
	id, _ := inbox.NewItemID()
	title := "Question"
	if len(input.Questions) > 0 {
		title = input.Questions[0].Question
	}
	opts := []wire.Option{}
	for i, q := range input.Questions {
		for j, o := range q.Options {
			opts = append(opts, wire.Option{ID: fmt.Sprintf("%d_%d", i, j), Label: o.Label})
		}
	}
	item := wire.Item{ID: id, Agent: terminalID, Kind: "question", Title: title, Options: opts, CreatedMs: inbox.NowMs()}
	_ = d.Inbox.Add(item)
	ans := d.waitAnswer(ctx, id, 580*time.Second)
	if ans == nil {
		return nil, false
	}
	return json.RawMessage(`{"hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"allow"}}`), true
}

func (d *Daemon) notification(terminalID string, in HookInput) (json.RawMessage, bool) {
	if in.Notification == nil {
		return json.RawMessage(`{}`), true
	}
	msg := in.Notification.Type
	w := &msg
	d.Agents.SetAgentFields(terminalID, "waiting", w)
	return json.RawMessage(`{}`), true
}

func (d *Daemon) stopHook(terminalID string, in HookInput) (json.RawMessage, bool) {
	d.Agents.SetAgentFields(terminalID, "done", nil)
	id, _ := inbox.NewItemID()
	body := in.LastAssistant
	item := wire.Item{ID: id, Agent: terminalID, Kind: "done", Title: "Finished", Body: &body, CreatedMs: inbox.NowMs()}
	_ = d.Inbox.Add(item)
	return json.RawMessage(`{}`), true
}

func (d *Daemon) waitAnswer(ctx context.Context, itemID string, timeout time.Duration) *inboxAnswer {
	ch := make(chan inboxAnswer, 1)
	d.mu.Lock()
	d.pending[itemID] = append(d.pending[itemID], ch)
	d.mu.Unlock()
	defer d.dropWaiter(itemID, ch)
	select {
	case ans := <-ch:
		return &ans
	case <-time.After(timeout):
		d.Inbox.Resolve(itemID)
		return nil
	case <-ctx.Done():
		return nil
	}
}

func (d *Daemon) dropWaiter(itemID string, ch chan inboxAnswer) {
	d.mu.Lock()
	defer d.mu.Unlock()
	waiters := d.pending[itemID]
	for i, w := range waiters {
		if w == ch {
			waiters = append(waiters[:i], waiters[i+1:]...)
			break
		}
	}
	if len(waiters) == 0 {
		delete(d.pending, itemID)
	} else {
		d.pending[itemID] = waiters
	}
}

func (d *Daemon) DeliverAnswer(itemID, decision string, option, text *string) {
	d.mu.Lock()
	waiters := append([]chan inboxAnswer(nil), d.pending[itemID]...)
	d.mu.Unlock()
	for _, ch := range waiters {
		select {
		case ch <- inboxAnswer{decision: decision, option: option, text: text}:
		default:
		}
	}
}

func allowPermission() json.RawMessage {
	return json.RawMessage(`{"hookSpecificOutput":{"hookEventName":"PermissionRequest","decision":{"behavior":"allow"}}}`)
}

func denyPermission() json.RawMessage {
	return json.RawMessage(`{"hookSpecificOutput":{"hookEventName":"PermissionRequest","decision":{"behavior":"deny"}}}`)
}

func isFileEdit(name string) bool {
	switch name {
	case "Edit", "Write", "MultiEdit", "NotebookEdit":
		return true
	default:
		return false
	}
}

func toolCommand(toolName string, raw json.RawMessage) string {
	var m map[string]any
	if json.Unmarshal(raw, &m) != nil {
		return string(raw)
	}
	str := func(k string) string { v, _ := m[k].(string); return v }
	known := func(keys ...string) bool {
		allowed := map[string]bool{}
		for _, k := range keys {
			allowed[k] = true
		}
		for k := range m {
			if !allowed[k] {
				return false
			}
		}
		return true
	}
	switch toolName {
	case "Bash":
		if known("command", "description", "timeout", "run_in_background") && str("command") != "" {
			return str("command")
		}
	case "Write":
		if known("file_path", "content") && str("file_path") != "" {
			return str("file_path") + "\n\n" + clipLines(str("content"), 12)
		}
	case "Edit":
		if known("file_path", "old_string", "new_string", "replace_all") && str("file_path") != "" {
			out := str("file_path")
			if all, _ := m["replace_all"].(bool); all {
				out += " (every match)"
			}
			return out + "\n\n" + prefixLines(clipLines(str("old_string"), 6), "- ") + "\n" + prefixLines(clipLines(str("new_string"), 6), "+ ")
		}
	case "WebFetch":
		if known("url", "prompt") && str("url") != "" {
			return str("url")
		}
	}
	return string(raw)
}

func clipLines(s string, max int) string {
	lines := strings.Split(strings.TrimRight(s, "\n"), "\n")
	if len(lines) <= max {
		return strings.Join(lines, "\n")
	}
	return strings.Join(lines[:max], "\n") + "\n(" + strconv.Itoa(len(lines)-max) + " more lines)"
}

func prefixLines(s, prefix string) string {
	lines := strings.Split(s, "\n")
	for i := range lines {
		lines[i] = prefix + lines[i]
	}
	return strings.Join(lines, "\n")
}

func approvalTitle(toolName string, raw json.RawMessage) string {
	if d := toolDescription(raw); d != "" {
		return d
	}
	return toolName
}

func approvalToolSubtitle(toolName, title string) string {
	if toolName == "" || strings.EqualFold(toolName, title) {
		return ""
	}
	return toolName
}

func toolDescription(raw json.RawMessage) string {
	var m map[string]any
	_ = json.Unmarshal(raw, &m)
	if d, ok := m["description"].(string); ok {
		d = strings.TrimSpace(d)
		if d != "" {
			return d
		}
	}
	return ""
}

func simpleModeFromAgent(terminalID string, store *agents.Store) string {
	a, ok := store.Get(terminalID)
	if !ok || a.Mode == nil {
		return "ask"
	}
	return *a.Mode
}
