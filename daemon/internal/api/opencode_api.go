package api

import (
	"context"
	"encoding/json"
	"errors"
	"time"

	"sh.simple/simpled/internal/agents"
	"sh.simple/simpled/internal/opencode"
	"sh.simple/simpled/internal/wire"
)

const openCodeCallTimeout = 5 * time.Second

func (sess *session) openCodeSessionFor(agentID string) (wire.Agent, string, bool) {
	agent, ok := sess.srv.Agents.Get(agentID)
	if !ok || agent.Kind != "opencode" || agent.Session == nil || *agent.Session == "" || sess.srv.OpenCode == nil {
		return agent, "", false
	}
	return agent, *agent.Session, true
}

func (sess *session) interruptOpenCode(ctx context.Context, id *int64, p json.RawMessage) bool {
	return sess.callOpenCode(ctx, id, p, map[string]any{"status": "interrupted"}, func(cctx context.Context, session string, _ string) error {
		return sess.srv.OpenCode.Interrupt(cctx, session)
	})
}

func (sess *session) sendOpenCode(ctx context.Context, id *int64, p json.RawMessage) bool {
	return sess.callOpenCode(ctx, id, p, map[string]any{}, func(cctx context.Context, session string, text string) error {
		if text == "" {
			return errEmptyPrompt
		}
		return sess.srv.OpenCode.Prompt(cctx, session, text)
	})
}

var errEmptyPrompt = errors.New("empty prompt")

func (sess *session) callOpenCode(ctx context.Context, id *int64, p json.RawMessage, result any, call func(context.Context, string, string) error) bool {
	var par struct {
		ID   string `json:"id"`
		Text string `json:"text"`
	}
	if json.Unmarshal(p, &par) != nil || par.ID == "" {
		return false
	}
	_, session, ok := sess.openCodeSessionFor(par.ID)
	if !ok {
		return false
	}
	cctx, cancel := context.WithTimeout(ctx, openCodeCallTimeout)
	defer cancel()
	if call(cctx, session, par.Text) != nil {
		return false
	}
	sess.writeResult(ctx, id, result)
	return true
}

func (sess *session) openCodeModels(ctx context.Context) wire.AgentModelsResult {
	result := agents.OpenCodeModelsResult()
	if sess.srv.OpenCode == nil {
		return result
	}
	cctx, cancel := context.WithTimeout(ctx, openCodeCallTimeout)
	defer cancel()
	models, err := sess.srv.OpenCode.Models(cctx)
	if err != nil {
		return result
	}
	connected, err := sess.srv.OpenCode.ConnectedProviders(cctx)
	if err != nil {
		connected = nil
	}
	result.Models = append(append([]wire.ModelInfo(nil), result.Models...), opencode.WireModelsForProviders(models, connected)...)
	return result
}

func (sess *session) configureOpenCode(ctx context.Context, session string, par wire.AgentConfigureParams) bool {
	if par.Model == nil || *par.Model == "default" {
		return true
	}
	variant := ""
	if par.Effort != nil {
		variant = *par.Effort
	}
	ref, ok := opencode.ParseModelKey(*par.Model, variant)
	if !ok {
		return false
	}
	cctx, cancel := context.WithTimeout(ctx, openCodeCallTimeout)
	defer cancel()
	return sess.srv.OpenCode.SwitchModel(cctx, session, ref) == nil
}
