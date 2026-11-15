package api

import (
	"context"
	"encoding/json"
	"errors"
	"time"

	"sh.simple/simpled/internal/agents"
	"sh.simple/simpled/internal/hc"
	"sh.simple/simpled/internal/herdr"
)

func (srv *Server) promptQueue() *promptQueue {
	srv.promptMu.Lock()
	defer srv.promptMu.Unlock()
	if srv.prompts == nil {
		srv.prompts = newPromptQueue(srv.agentWorking, srv.deviceAuthorized, srv.deliverPrompt)
	}
	return srv.prompts
}

func (srv *Server) agentWorking(id string) bool {
	if srv.Agents == nil {
		return false
	}
	a, ok := srv.Agents.Get(id)
	return ok && a.Status == "working"
}

func (srv *Server) deliverPrompt(ctx context.Context, id, text string) error {
	if srv.Agents != nil && srv.Agents.AgentRuntime(id) == "herdr" {
		if srv.Herdr == nil || srv.Herdr.Status() != herdr.StatusUp {
			return errors.New("herdr unavailable")
		}
		target, ok := srv.Agents.HerdrTarget(id)
		if !ok {
			return errors.New("agent not found")
		}
		if err := srv.Herdr.AgentPrompt(ctx, target, text); err != nil {
			return err
		}
		srv.noteDelivered(id, text)
		return nil
	}
	if srv.HC == nil || srv.HC.Status() != hc.StatusUp {
		return errors.New("hc unavailable")
	}
	var out json.RawMessage
	err := srv.HC.Call(ctx, "terminal.send", map[string]any{"terminal": id, "text": text}, &out)
	if err != nil {
		return err
	}
	srv.noteDelivered(id, text)
	return nil
}

func (srv *Server) deviceAuthorized(device string) bool {
	if srv.Store == nil || device == "" {
		return true
	}
	list, err := srv.Store.LoadDevices()
	if err != nil {
		return false
	}
	for _, d := range list {
		if d.ID == device {
			return true
		}
	}
	return false
}

func (sess *session) clearQueuedPrompts(p json.RawMessage) {
	var in struct {
		ID string `json:"id"`
	}
	if json.Unmarshal(p, &in) == nil && in.ID != "" {
		sess.srv.promptQueue().Clear(in.ID)
	}
}

func (sess *session) queueSend(ctx context.Context, id *int64, p json.RawMessage) bool {
	var in struct {
		ID   string `json:"id"`
		Text string `json:"text"`
	}
	if json.Unmarshal(p, &in) != nil || in.ID == "" || in.Text == "" || sess.srv == nil || sess.srv.Agents == nil {
		return false
	}
	if _, ok := sess.srv.Agents.Get(in.ID); !ok {
		return false
	}
	queued, err := sess.srv.promptQueue().Submit(ctx, in.ID, in.Text, sess.deviceID)
	if errors.Is(err, errPromptQueueFull) {
		sess.writeErr(ctx, id, "bad_request", err.Error())
		return true
	}
	if err != nil {
		sess.srv.failIfStillWaiting(in.ID, in.Text, time.Now().Add(-2*time.Second).UnixMilli(), agents.CouldNotSend())
		sess.writeErr(ctx, id, "hc_unavailable", err.Error())
		return true
	}
	sess.writeResult(ctx, id, map[string]any{"queued": queued})
	return true
}
