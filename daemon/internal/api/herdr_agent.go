package api

import (
	"context"
	"encoding/json"

	"sh.simple/simpled/internal/herdr"
)

func (sess *session) herdrTarget(p json.RawMessage) (string, bool) {
	var in struct {
		ID string `json:"id"`
	}
	if json.Unmarshal(p, &in) != nil || in.ID == "" {
		return "", false
	}
	if sess.srv == nil || sess.srv.Agents == nil {
		return "", false
	}
	if sess.srv.Agents.AgentRuntime(in.ID) != "herdr" {
		return "", false
	}
	return in.ID, true
}

func (sess *session) agentHerdr(ctx context.Context, id *int64, op string, p json.RawMessage) bool {
	agentID, ok := sess.herdrTarget(p)
	if !opSupportedHerdr(op) || !ok {
		return false
	}
	if sess.srv.Herdr == nil || sess.srv.Herdr.Status() != herdr.StatusUp {
		sess.writeErr(ctx, id, "hc_unavailable", "")
		return true
	}
	target, ok := sess.srv.Agents.HerdrTarget(agentID)
	if !ok {
		sess.writeErr(ctx, id, "not_found", "")
		return true
	}
	switch op {
	case "send":
		var in struct {
			Text string `json:"text"`
		}
		if json.Unmarshal(p, &in) != nil || in.Text == "" {
			sess.writeErr(ctx, id, "bad_request", "")
			return true
		}
		if err := sess.srv.Herdr.AgentPrompt(ctx, target, in.Text); err != nil {
			sess.writeErr(ctx, id, "hc_unavailable", err.Error())
			return true
		}
		sess.writeResult(ctx, id, map[string]any{})
	case "keys":
		var in struct {
			Keys []string `json:"keys"`
		}
		if json.Unmarshal(p, &in) != nil || len(in.Keys) == 0 {
			sess.writeErr(ctx, id, "bad_request", "")
			return true
		}
		if err := sess.srv.Herdr.AgentSendKeys(ctx, target, in.Keys); err != nil {
			sess.writeErr(ctx, id, "hc_unavailable", err.Error())
			return true
		}
		sess.writeResult(ctx, id, map[string]any{})
	case "interrupt":
		if err := sess.srv.Herdr.Interrupt(ctx, target); err != nil {
			sess.writeErr(ctx, id, "hc_unavailable", err.Error())
			return true
		}
		sess.writeResult(ctx, id, map[string]any{"status": "stopped"})
	case "screen":
		var in struct {
			Lines *int `json:"lines,omitempty"`
		}
		_ = json.Unmarshal(p, &in)
		text, err := sess.srv.Herdr.AgentRead(ctx, target, "recent_unwrapped", in.Lines)
		if err != nil {
			sess.writeErr(ctx, id, "hc_unavailable", err.Error())
			return true
		}
		sess.writeResult(ctx, id, map[string]any{"text": text, "rows": 0, "cols": 0})
	case "kill", "remove":
		sess.writeErr(ctx, id, "agent_unsupported", "")
	default:
		return false
	}
	return true
}

func opSupportedHerdr(op string) bool {
	switch op {
	case "send", "keys", "interrupt", "screen", "kill", "remove":
		return true
	default:
		return false
	}
}
