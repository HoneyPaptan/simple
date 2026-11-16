package api

import (
	"context"
	"log"
	"strings"
	"time"

	"sh.simple/simpled/internal/agents"
	"sh.simple/simpled/internal/wire"
)

func (srv *Server) noteDelivered(id, prompt string) {
	if srv == nil || srv.Agents == nil || id == "" {
		return
	}
	log.Printf("agent.send %s", id)
	srv.Agents.ClearSendFailure(id)
	srv.watchSend(id, prompt, true)
}

func (srv *Server) latchFailedSend(id, text string) {
	if srv == nil {
		return
	}
	srv.sends.releaseAgent(id)
	if srv.Agents != nil {
		srv.Agents.LatchSendFailure(id, text)
	}
}

func (srv *Server) failIfStillWaiting(id, prompt string, since int64, fallback string) {
	if srv == nil || srv.Agents == nil || id == "" {
		return
	}
	if srv.agentWorking(id) || srv.promptLanded(id, prompt, since) {
		return
	}
	text := fallback
	if line, ok := agents.ClassifyIdle(srv.readAgentScreen(context.Background(), id), prompt); ok {
		text = line
	}
	if strings.TrimSpace(text) == "" {
		text = agents.CouldNotSend()
	}
	srv.latchFailedSend(id, text)
	srv.watchSend(id, prompt, false)
}

func (srv *Server) watchSend(id, prompt string, classify bool) {
	kind := "shell"
	if a, ok := srv.Agents.Get(id); ok && a.Kind != "" {
		kind = a.Kind
	}
	since := time.Now().UnixMilli() - 2000
	ctx := srv.replaceSendWatch(id)
	go agents.RunSendWatch(ctx, agents.SendWatch{
		Kind:     kind,
		Prompt:   prompt,
		Classify: classify,
		Read: func(ctx context.Context) string {
			return srv.readAgentScreen(ctx, id)
		},
		TurnStarted: func() bool {
			return srv.agentWorking(id)
		},
		PromptLanded: func() bool {
			return srv.promptLanded(id, prompt, since)
		},
		Fail: func(text string) {
			srv.latchFailedSend(id, text)
		},
		Clear: func() {
			srv.Agents.ClearSendFailure(id)
		},
	})
}

func (srv *Server) replaceSendWatch(id string) context.Context {
	srv.sendWatchMu.Lock()
	defer srv.sendWatchMu.Unlock()
	if srv.sendWatch == nil {
		srv.sendWatch = make(map[string]context.CancelFunc)
	}
	if cancel, ok := srv.sendWatch[id]; ok {
		cancel()
	}
	ctx, cancel := context.WithCancel(context.Background())
	srv.sendWatch[id] = cancel
	return ctx
}

func (srv *Server) readAgentScreen(ctx context.Context, id string) string {
	if srv == nil {
		return ""
	}
	if srv.Screens != nil {
		if text, ok := srv.Screens.readScreen(ctx, id); ok {
			return text
		}
	}
	if srv.Agents != nil && srv.Agents.AgentRuntime(id) == "herdr" && srv.Herdr != nil {
		target, ok := srv.Agents.HerdrTarget(id)
		if !ok {
			return ""
		}
		text, err := srv.Herdr.AgentRead(ctx, target, "recent_unwrapped", nil)
		if err != nil {
			return ""
		}
		return text
	}
	if srv.HC == nil {
		return ""
	}
	var out struct {
		Text string `json:"text"`
	}
	if srv.HC.Call(ctx, "terminal.read", map[string]any{"terminal": id}, &out) != nil {
		return ""
	}
	return out.Text
}

func (srv *Server) promptLanded(id, prompt string, since int64) bool {
	if srv == nil || srv.Transcripts == nil || srv.Agents == nil || strings.TrimSpace(prompt) == "" {
		return false
	}
	agent, ok := srv.Agents.Get(id)
	if !ok {
		return false
	}
	msgs, _, _, err := srv.Transcripts.ReadPage(id, agent, nil, 40)
	if err != nil || len(msgs) == 0 {
		return false
	}
	lines := make([]string, 0, len(msgs))
	ts := make([]int64, 0, len(msgs))
	for _, m := range msgs {
		if m.Role != "user" || m.Text == nil {
			continue
		}
		lines = append(lines, *m.Text)
		ts = append(ts, m.Ts)
	}
	return agents.PromptLanded(prompt, lines, since, func(i int) int64 {
		return ts[i]
	})
}

func (sess *session) appendSendEvent(id string, msgs []wire.Message, before *string) []wire.Message {
	if before != nil || sess == nil || sess.srv == nil || sess.srv.Agents == nil {
		return msgs
	}
	ev, ok := sess.srv.Agents.SendEvent(id)
	if !ok {
		return msgs
	}
	return append(msgs, ev)
}
