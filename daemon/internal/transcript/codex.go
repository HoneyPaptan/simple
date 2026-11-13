package transcript

import (
	"bufio"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"os"
	"regexp"
	"strings"
	"time"

	"sh.simple/simpled/internal/wire"
)

var codexExitCodeRE = regexp.MustCompile(`(?m)^Process exited with code (-?\d+)`)

func ParseCodexJSONL(path string) ([]wire.Message, error) {
	f, err := os.Open(path)
	if err != nil {
		return nil, err
	}
	defer f.Close()
	var messages []wire.Message
	toolUses := map[string]*wire.Tool{}
	seenIDs := map[string]bool{}
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
		typ, _ := row["type"].(string)
		ts := codexLineTS(row)
		switch typ {
		case "response_item":
			codexAppendResponseItem(row, ts, toolUses, seenIDs, &messages)
		case "event_msg":
			codexAppendEventMsg(row, ts, toolUses, seenIDs, &messages)
		}
	}
	return messages, br.Err()
}

func codexPushTextMessage(seen map[string]bool, messages *[]wire.Message, msg wire.Message) {
	if seen[msg.ID] {
		return
	}
	seen[msg.ID] = true
	*messages = append(*messages, msg)
}

func codexAppendResponseItem(row map[string]any, ts int64, toolUses map[string]*wire.Tool, seen map[string]bool, messages *[]wire.Message) {
	payload, _ := row["payload"].(map[string]any)
	if payload == nil {
		return
	}
	itemType, _ := payload["type"].(string)
	switch itemType {
	case "message":
		role, _ := payload["role"].(string)
		if role != "user" && role != "assistant" {
			return
		}
		text := codexContentText(payload["content"])
		if text == "" || role == "user" && codexSkipUserText(text) {
			return
		}
		id := codexItemID(payload, role, text)
		codexPushTextMessage(seen, messages, wire.Message{ID: id, Role: role, Ts: ts, Text: &text})
	case "agent_message":
		text := codexAgentMessageText(payload)
		if text == "" {
			return
		}
		id := codexItemID(payload, "assistant", text)
		textCopy := text
		codexPushTextMessage(seen, messages, wire.Message{ID: id, Role: "assistant", Ts: ts, Text: &textCopy})
	case "reasoning":
		if text := codexReasoningText(payload["summary"]); text != "" {
			codexPushTextMessage(seen, messages, wire.Message{ID: wireMsgID("thinking", text), Role: "thinking", Ts: ts, Text: &text})
		}
	case "function_call":
		callID, _ := payload["call_id"].(string)
		if callID == "" {
			return
		}
		name, _ := payload["name"].(string)
		args, _ := payload["arguments"].(string)
		tool := codexToolFromFunction(name, args)
		toolUses[callID] = &tool
		*messages = append(*messages, wire.Message{ID: callID, Role: "tool", Ts: ts, Tool: &tool})
	case "custom_tool_call":
		callID, _ := payload["call_id"].(string)
		if callID == "" {
			return
		}
		name, _ := payload["name"].(string)
		input, _ := payload["input"].(string)
		tool := codexToolFromCustom(name, input)
		toolUses[callID] = &tool
		*messages = append(*messages, wire.Message{ID: callID, Role: "tool", Ts: ts, Tool: &tool})
	case "local_shell_call":
		callID := codexCallID(payload)
		if callID == "" {
			return
		}
		tool := codexToolFromLocalShell(payload)
		toolUses[callID] = &tool
		*messages = append(*messages, wire.Message{ID: callID, Role: "tool", Ts: ts, Tool: &tool})
	case "web_search_call":
		callID := codexCallID(payload)
		if callID == "" {
			return
		}
		tool := codexToolFromWebSearch(payload)
		toolUses[callID] = &tool
		*messages = append(*messages, wire.Message{ID: callID, Role: "tool", Ts: ts, Tool: &tool})
	case "function_call_output", "custom_tool_call_output":
		callID := codexOutputCallID(payload)
		if callID == "" {
			return
		}
		text := codexFunctionOutputText(payload["output"])
		out := trimOutput(text)
		status := codexOutputStatus(text)
		if t, ok := toolUses[callID]; ok {
			t.Output = &out
			t.Status = status
		}
	}
}

func codexAppendEventMsg(row map[string]any, ts int64, toolUses map[string]*wire.Tool, seen map[string]bool, messages *[]wire.Message) {
	payload, _ := row["payload"].(map[string]any)
	if payload == nil {
		return
	}
	evType, _ := payload["type"].(string)
	switch evType {
	case "user_message":
		text, _ := payload["message"].(string)
		if text == "" || codexSkipUserText(text) {
			return
		}
		id := wireMsgID("user", text)
		codexPushTextMessage(seen, messages, wire.Message{ID: id, Role: "user", Ts: ts, Text: &text})
	case "agent_message":
		text, _ := payload["message"].(string)
		if text == "" {
			return
		}
		id := wireMsgID("assistant", text)
		codexPushTextMessage(seen, messages, wire.Message{ID: id, Role: "assistant", Ts: ts, Text: &text})
	case "turn_aborted":
		codexMarkRunningToolsError(messages, toolUses)
		text := codexTurnAbortedText(payload)
		id := wireMsgID("assistant", text)
		codexPushTextMessage(seen, messages, wire.Message{ID: id, Role: "assistant", Ts: ts, Text: &text})
	}
}

func codexMarkRunningToolsError(messages *[]wire.Message, toolUses map[string]*wire.Tool) {
	for id, t := range toolUses {
		if t.Status == "running" {
			t.Status = "error"
			for i := range *messages {
				if (*messages)[i].ID == id && (*messages)[i].Tool != nil {
					(*messages)[i].Tool.Status = "error"
				}
			}
		}
	}
}

func codexTurnAbortedText(payload map[string]any) string {
	base := "Codex stopped"
	reason, _ := payload["reason"].(string)
	if reason == "" {
		return base
	}
	return base + ": " + reason
}

func codexSkipUserText(text string) bool {
	if strings.HasPrefix(text, "<environment_context>") {
		return true
	}
	if strings.HasPrefix(text, "<user_instructions>") {
		return true
	}
	if strings.HasPrefix(text, "<permissions instructions>") {
		return true
	}
	if strings.HasPrefix(text, "# AGENTS.md instructions") {
		return true
	}
	if strings.HasPrefix(text, "<turn_aborted>") {
		return true
	}
	return false
}

func codexToolFromFunction(name, args string) wire.Tool {
	summary, input, diff := codexFunctionToolMeta(name, args)
	in := input
	if len(in) > maxToolOutput {
		in = in[:maxToolOutput]
	}
	return wire.Tool{
		Name:    name,
		Summary: summary,
		Input:   &in,
		Status:  "running",
		Diff:    diff,
	}
}

func codexToolFromCustom(name, input string) wire.Tool {
	summary, diff := codexCustomToolMeta(name, input)
	in := input
	if len(in) > maxToolOutput {
		in = in[:maxToolOutput]
	}
	return wire.Tool{
		Name:    name,
		Summary: summary,
		Input:   &in,
		Status:  "running",
		Diff:    diff,
	}
}

func codexToolFromLocalShell(payload map[string]any) wire.Tool {
	cmd := codexLocalShellCommand(payload)
	summary := oneLine(cmd)
	in := codexShellInputJSON(cmd)
	return wire.Tool{
		Name:    "local_shell",
		Summary: summary,
		Input:   &in,
		Status:  "running",
	}
}

func codexToolFromWebSearch(payload map[string]any) wire.Tool {
	query := codexWebSearchQuery(payload)
	return wire.Tool{
		Name:    "web_search",
		Summary: oneLine(query),
		Status:  "running",
	}
}

func codexFunctionToolMeta(name, args string) (summary, input string, diff *string) {
	input = args
	switch name {
	case "shell", "exec_command":
		cmd := codexShellCommandFromArgs(args)
		summary = oneLine(cmd)
		input = codexShellInputJSON(cmd)
	case "apply_patch":
		summary = codexPatchSummary(args)
		d := codexPatchUnifiedDiff(args)
		diff = &d
	case "update_plan":
		summary = codexPlanSummaryFromArgs(args)
	default:
		summary = oneLine(args)
	}
	return summary, input, diff
}

func codexCustomToolMeta(name, input string) (summary string, diff *string) {
	if name == "apply_patch" {
		summary = codexPatchSummary(input)
		d := codexPatchUnifiedDiff(input)
		return summary, &d
	}
	return oneLine(input), nil
}

func codexShellCommandFromArgs(args string) string {
	var m map[string]any
	if json.Unmarshal([]byte(args), &m) != nil {
		return strings.TrimSpace(args)
	}
	for _, key := range []string{"command", "cmd"} {
		if c := codexJoinCommand(m[key]); c != "" {
			return codexStripBashLC(c)
		}
	}
	if c, ok := m["command"].(string); ok {
		return codexStripBashLC(c)
	}
	return oneLine(args)
}

func codexLocalShellCommand(payload map[string]any) string {
	action, _ := payload["action"].(map[string]any)
	if action == nil {
		return ""
	}
	return codexStripBashLC(codexJoinCommand(action["command"]))
}

func codexJoinCommand(raw any) string {
	switch v := raw.(type) {
	case string:
		return strings.TrimSpace(v)
	case []any:
		var parts []string
		for _, p := range v {
			if s, ok := p.(string); ok {
				parts = append(parts, s)
			}
		}
		return strings.Join(parts, " ")
	}
	return ""
}

func codexStripBashLC(cmd string) string {
	cmd = strings.TrimSpace(cmd)
	fields := strings.Fields(cmd)
	if len(fields) >= 3 && fields[0] == "bash" && fields[1] == "-lc" {
		return strings.TrimSpace(strings.Join(fields[2:], " "))
	}
	prefix := "bash -lc "
	if strings.HasPrefix(cmd, prefix) {
		return strings.TrimSpace(cmd[len(prefix):])
	}
	return cmd
}

func codexShellInputJSON(cmd string) string {
	b, _ := json.Marshal(map[string]string{"command": cmd})
	return string(b)
}

func codexPlanSummaryFromArgs(args string) string {
	var m map[string]any
	if json.Unmarshal([]byte(args), &m) != nil {
		return oneLine(args)
	}
	plan, ok := m["plan"].([]any)
	if !ok {
		return oneLine(args)
	}
	return codexPlanSummary(plan)
}

func codexPlanSummary(plan []any) string {
	done := 0
	for _, t := range plan {
		if item, ok := t.(map[string]any); ok {
			st, _ := item["status"].(string)
			if st == "completed" {
				done++
			}
		}
	}
	return fmt.Sprintf("%d of %d done", done, len(plan))
}

func codexPatchSummary(patch string) string {
	for _, line := range strings.Split(patch, "\n") {
		line = strings.TrimSpace(line)
		for _, prefix := range []string{"*** Update File: ", "*** Add File: ", "*** Delete File: "} {
			if strings.HasPrefix(line, prefix) {
				return oneLine(strings.TrimPrefix(line, prefix))
			}
		}
	}
	return oneLine(patch)
}

func codexPatchUnifiedDiff(patch string) string {
	var lines []string
	for _, line := range strings.Split(patch, "\n") {
		if len(line) == 0 {
			continue
		}
		if line[0] == '+' || line[0] == '-' {
			lines = append(lines, line)
		}
	}
	return strings.Join(lines, "\n")
}

func codexWebSearchQuery(payload map[string]any) string {
	action, _ := payload["action"].(map[string]any)
	if action == nil {
		return ""
	}
	if q, ok := action["query"].(string); ok && q != "" {
		return q
	}
	if qs, ok := action["queries"].([]any); ok && len(qs) > 0 {
		if s, ok := qs[0].(string); ok {
			return s
		}
	}
	return ""
}

func codexFunctionOutputText(raw any) string {
	if raw == nil {
		return ""
	}
	switch v := raw.(type) {
	case string:
		return v
	case []any:
		var parts []string
		for _, item := range v {
			block, _ := item.(map[string]any)
			if block == nil {
				continue
			}
			if t, ok := block["text"].(string); ok && t != "" {
				parts = append(parts, t)
			}
			if c, ok := block["content"].(string); ok && c != "" {
				parts = append(parts, c)
			}
		}
		return strings.Join(parts, "\n")
	case map[string]any:
		if c, ok := v["content"].(string); ok {
			return c
		}
		b, _ := json.Marshal(v)
		return string(b)
	}
	return fmt.Sprint(raw)
}

func codexOutputStatus(text string) string {
	if codexOutputFailed(text) {
		return "error"
	}
	return "ok"
}

func codexOutputFailed(text string) bool {
	if m := codexExitCodeRE.FindStringSubmatch(text); len(m) == 2 {
		if m[1] != "0" {
			return true
		}
	}
	var obj map[string]any
	if json.Unmarshal([]byte(strings.TrimSpace(text)), &obj) == nil {
		if ec, ok := obj["exit_code"].(float64); ok && int(ec) != 0 {
			return true
		}
		if meta, ok := obj["metadata"].(map[string]any); ok {
			if ec, ok := meta["exit_code"].(float64); ok && int(ec) != 0 {
				return true
			}
		}
	}
	return false
}

func codexOutputCallID(payload map[string]any) string {
	if s, _ := payload["call_id"].(string); s != "" {
		return s
	}
	return ""
}

func codexCallID(payload map[string]any) string {
	if s, _ := payload["call_id"].(string); s != "" {
		return s
	}
	if idRaw, ok := payload["id"]; ok && idRaw != nil {
		switch v := idRaw.(type) {
		case string:
			return v
		case map[string]any:
			if s, ok := v["id"].(string); ok {
				return s
			}
		}
	}
	return ""
}

func codexLineTS(row map[string]any) int64 {
	if s, ok := row["timestamp"].(string); ok && s != "" {
		t, err := time.Parse(time.RFC3339Nano, s)
		if err == nil {
			return t.UnixMilli()
		}
		t, err = time.Parse(time.RFC3339, s)
		if err == nil {
			return t.UnixMilli()
		}
	}
	return time.Now().UnixMilli()
}

func codexContentText(raw any) string {
	content, ok := raw.([]any)
	if !ok {
		return ""
	}
	var parts []string
	for _, blockRaw := range content {
		block, _ := blockRaw.(map[string]any)
		if block == nil {
			continue
		}
		typ, _ := block["type"].(string)
		switch typ {
		case "input_text", "output_text":
			if t, ok := block["text"].(string); ok && t != "" {
				parts = append(parts, t)
			}
		default:
			if t, ok := block["text"].(string); ok && t != "" {
				parts = append(parts, t)
			}
		}
	}
	return strings.Join(parts, "\n")
}

func codexAgentMessageText(payload map[string]any) string {
	if t, ok := payload["text"].(string); ok {
		return t
	}
	return codexContentText(payload["content"])
}

func codexItemID(payload map[string]any, role, text string) string {
	if idRaw, ok := payload["id"]; ok && idRaw != nil {
		switch v := idRaw.(type) {
		case string:
			if v != "" {
				return v
			}
		case map[string]any:
			if s, ok := v["id"].(string); ok && s != "" {
				return s
			}
		}
	}
	return wireMsgID(role, text)
}

func wireMsgID(role, text string) string {
	h := sha256.Sum256([]byte(role + "\x00" + text))
	return hex.EncodeToString(h[:8])
}

func codexReasoningText(raw any) string {
	parts, _ := raw.([]any)
	var out []string
	for _, p := range parts {
		block, _ := p.(map[string]any)
		if t, ok := block["text"].(string); ok && strings.TrimSpace(t) != "" {
			out = append(out, strings.TrimSpace(t))
		}
	}
	return strings.Join(out, "\n\n")
}
