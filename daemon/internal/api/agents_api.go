package api

import (
	"context"
	"encoding/json"
	"strings"
	"time"

	"sh.simple/simpled/internal/files"

	"sh.simple/simpled/internal/agents"
	"sh.simple/simpled/internal/detect"
	"sh.simple/simpled/internal/hc"
	"sh.simple/simpled/internal/herdr"
	"sh.simple/simpled/internal/wire"
)

func (sess *session) agentCommands(ctx context.Context, id *int64, p json.RawMessage) {
	var par wire.AgentCommandsParams
	if json.Unmarshal(p, &par) != nil || par.ID == "" {
		sess.writeErr(ctx, id, "bad_request", "")
		return
	}
	a, ok := sess.srv.Agents.Get(par.ID)
	if !ok {
		sess.writeErr(ctx, id, "not_found", "")
		return
	}
	if sess.srv.Commands == nil {
		sess.writeResult(ctx, id, wire.AgentCommandsResult{Commands: []wire.AgentCommand{}})
		return
	}
	list, err := sess.srv.Commands.ForAgent(ctx, a)
	if err != nil {
		sess.writeErr(ctx, id, "internal", "")
		return
	}
	sess.writeResult(ctx, id, wire.AgentCommandsResult{Commands: list})
}

func (sess *session) agentStart(ctx context.Context, id *int64, p json.RawMessage) {
	var par wire.AgentStartParams
	if json.Unmarshal(p, &par) != nil || par.Cwd == "" {
		sess.writeErr(ctx, id, "bad_request", "")
		return
	}
	cwd, cerr := files.ResolvePath(par.Cwd)
	if cerr != nil {
		sess.writeErr(ctx, id, "bad_request", "")
		return
	}
	par.Cwd = cwd
	if !agents.LaunchKinds[par.Kind] {
		sess.writeErr(ctx, id, "agent_unsupported", "")
		return
	}
	runtime := par.Runtime
	if runtime == "" {
		runtime = "hc"
	}
	if runtime == "herdr" {
		if sess.srv.Herdr == nil || sess.srv.Herdr.Status() != herdr.StatusUp {
			sess.writeErr(ctx, id, "hc_unavailable", "")
			return
		}
		startParams := agents.StartClaudeParams{
			Cwd: par.Cwd, Prompt: par.Prompt, Model: par.Model, Effort: par.Effort, Mode: par.Mode, Worktree: par.Worktree,
		}
		prompt := ""
		if par.Prompt != nil {
			prompt = strings.TrimSpace(*par.Prompt)
		}
		since := time.Now().UnixMilli()
		startParams.AfterPrompt = func(agentID string, sent bool) {
			if sent {
				sess.srv.noteDelivered(agentID, prompt)
				return
			}
			sess.srv.failIfStillWaiting(agentID, prompt, since, agents.DidNotStart(par.Kind))
		}
		a, err := agents.StartInHerdr(ctx, sess.srv.Herdr, par.Kind, startParams)
		if err != nil {
			sess.writeErr(ctx, id, "internal", err.Error())
			return
		}
		sess.srv.Agents.Upsert(a)
		sess.writeResult(ctx, id, map[string]any{"agent": a})
		return
	}
	if sess.srv.HC.Status() != hc.StatusUp {
		sess.writeErr(ctx, id, "hc_unavailable", "")
		return
	}
	startParams := agents.StartClaudeParams{
		Cwd: par.Cwd, Prompt: par.Prompt, Model: par.Model, Effort: par.Effort, Mode: par.Mode, Worktree: par.Worktree,
	}
	var a wire.Agent
	var err error
	switch par.Kind {
	case "claude":
		a, _, err = agents.StartClaude(ctx, sess.srv.HC, sess.srv.SimpledPath, startParams)
	case "codex":
		a, _, err = agents.StartCodex(ctx, sess.srv.HC, startParams)
	case "cursor":
		a, _, err = agents.StartCursor(ctx, sess.srv.HC, startParams)
	case "opencode":
		a, _, err = agents.StartOpenCode(ctx, sess.srv.HC, startParams)
	case "fx":
		a, _, err = agents.StartFx(ctx, sess.srv.HC, startParams)
	case "shell":
		a, _, err = agents.StartShell(ctx, sess.srv.HC, startParams)
	default:
		if agents.IsTerminalLaunchKind(par.Kind) {
			a, _, err = agents.StartTerminalKind(ctx, sess.srv.HC, par.Kind, startParams)
		} else {
			sess.writeErr(ctx, id, "agent_unsupported", "")
			return
		}
	}
	if err != nil {
		sess.writeErr(ctx, id, "internal", err.Error())
		return
	}
	sess.srv.Agents.Upsert(a)
	if par.Prompt != nil && strings.TrimSpace(*par.Prompt) != "" {
		prompt := strings.TrimSpace(*par.Prompt)
		since := time.Now().UnixMilli()
		agents.SendPromptWhenQuiet(ctx, sess.srv.HC, a.ID, par.Kind, prompt, func(sent bool) {
			if sent {
				sess.srv.noteDelivered(a.ID, prompt)
				return
			}
			sess.srv.failIfStillWaiting(a.ID, prompt, since, agents.DidNotStart(par.Kind))
		})
	}
	sess.writeResult(ctx, id, map[string]any{"agent": a})
}

func (sess *session) agentConfigure(ctx context.Context, id *int64, p json.RawMessage) {
	var par wire.AgentConfigureParams
	if json.Unmarshal(p, &par) != nil || par.ID == "" {
		sess.writeErr(ctx, id, "bad_request", "")
		return
	}
	agent, ok := sess.srv.Agents.Get(par.ID)
	if !ok {
		sess.writeErr(ctx, id, "not_found", "")
		return
	}
	switch agent.Kind {
	case "claude":
		if par.Model != nil {
			cmd := "/model " + *par.Model
			_ = sess.srv.HC.Call(ctx, "terminal.send", map[string]any{"terminal": par.ID, "text": cmd}, &json.RawMessage{})
			agent.Model = par.Model
		}
		if par.Effort != nil {
			_ = sess.srv.HC.Call(ctx, "terminal.label", map[string]any{
				"terminal": par.ID,
				"labels":   map[string]string{"simple.effort": *par.Effort},
			}, &json.RawMessage{})
			agent.Effort = par.Effort
		}
	case "opencode":
		if par.Model != nil {
			_, session, ok := sess.openCodeSessionFor(par.ID)
			if !ok || !sess.configureOpenCode(ctx, session, par) {
				sess.writeErr(ctx, id, "internal", "could not switch the OpenCode model")
				return
			}
			agent.Model = par.Model
			agent.Effort = par.Effort
		}
	}
	if par.Mode != nil {
		_ = sess.srv.HC.Call(ctx, "terminal.label", map[string]any{
			"terminal": par.ID,
			"labels":   map[string]string{"simple.mode": *par.Mode},
		}, &json.RawMessage{})
		agent.Mode = par.Mode
	}
	sess.srv.Agents.Upsert(agent)
	sess.writeResult(ctx, id, map[string]any{"agent": agent})
}

func (sess *session) agentTranscript(ctx context.Context, id *int64, p json.RawMessage) {
	var par wire.AgentTranscriptParams
	if json.Unmarshal(p, &par) != nil || par.ID == "" {
		sess.writeErr(ctx, id, "bad_request", "")
		return
	}
	agent, ok := sess.srv.Agents.Get(par.ID)
	if !ok {
		sess.writeErr(ctx, id, "not_found", "")
		return
	}
	limit := 50
	if par.Limit != nil {
		limit = *par.Limit
	}
	msgs, before, since, err := sess.srv.Transcripts.ReadPage(par.ID, agent, par.Before, limit)
	if err != nil {
		sess.writeErr(ctx, id, "internal", err.Error())
		return
	}
	msgs = sess.appendSendEvent(par.ID, msgs, par.Before)
	sess.writeResult(ctx, id, wire.AgentTranscriptResult{Messages: msgs, Before: before, Since: since})
}

func (sess *session) agentWatch(ctx context.Context, id *int64, p json.RawMessage) {
	var par wire.AgentWatchParams
	if json.Unmarshal(p, &par) != nil || par.ID == "" {
		sess.writeErr(ctx, id, "bad_request", "")
		return
	}
	sess.setWatched(par.ID, par.Screen)
	if par.Screen {
		if sess.srv.Screens != nil {
			sess.srv.Screens.Start(par.ID, sess)
		}
	} else {
		if sess.srv.Screens != nil {
			sess.srv.Screens.Stop(par.ID, sess)
		}
	}
	sess.writeResult(ctx, id, map[string]any{})
}

func (sess *session) inboxAnswer(ctx context.Context, id *int64, p json.RawMessage) {
	var par wire.InboxAnswerParams
	if json.Unmarshal(p, &par) != nil || par.ID == "" {
		sess.writeErr(ctx, id, "bad_request", "")
		return
	}
	if err := sess.srv.Inbox.Answer(par.ID, par.Decision, par.Option, par.Text); err != nil {
		sess.writeErr(ctx, id, "not_found", "")
		return
	}
	item, had := sess.srv.Inbox.Get(par.ID)
	if had && item.Kind == "prompt" && item.Detector != nil {
		keys := detect.KeysForAnswer(item, par.Decision, par.Option, par.Text)
		if len(keys) > 0 {
			sess.sendAnswerKeys(ctx, item.Agent, keys)
		}
	}
	if sess.srv.Approvals != nil {
		sess.srv.Approvals.Answer(ctx, par.ID, par.Decision)
	}
	if sess.srv.Hooks != nil {
		sess.srv.Hooks.DeliverAnswer(par.ID, par.Decision, par.Option, par.Text)
	}
	sess.writeResult(ctx, id, map[string]any{})
}

func (sess *session) sendAnswerKeys(ctx context.Context, agentID string, keys []string) {
	if sess.srv.Agents != nil && sess.srv.Agents.AgentRuntime(agentID) == "herdr" {
		if target, ok := sess.srv.Agents.HerdrTarget(agentID); ok && sess.srv.Herdr != nil {
			_ = sess.srv.Herdr.AgentSendKeys(ctx, target, keys)
		}
		return
	}
	if sess.srv.HC.Status() == hc.StatusUp {
		_ = sess.srv.HC.Call(ctx, "terminal.keys", map[string]any{
			"terminal": agentID,
			"keys":     keys,
		}, &json.RawMessage{})
	}
}

func (sess *session) setWatched(agentID string, on bool) {
	sess.mu.Lock()
	defer sess.mu.Unlock()
	if sess.closed || sess.watched[agentID] == on {
		return
	}
	if on {
		if sess.watched == nil {
			sess.watched = map[string]bool{}
		}
		sess.watched[agentID] = true
	} else {
		delete(sess.watched, agentID)
	}
	sess.srv.Transcripts.SetWatch(agentID, on)
}
