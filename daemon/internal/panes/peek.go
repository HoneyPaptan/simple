package panes

import (
	"context"
	"strings"
	"time"

	"sh.simple/simpled/internal/agents"
	"sh.simple/simpled/internal/hc"
	"sh.simple/simpled/internal/herdr"
	"sh.simple/simpled/internal/wire"
)

func clampPeek(ids []string, lines int) (idsOut []string, linesOut int) {
	linesOut = lines
	if linesOut <= 0 {
		linesOut = MaxPeekLines
	}
	if linesOut > MaxPeekLines {
		linesOut = MaxPeekLines
	}
	if len(ids) > MaxPeekPanes {
		ids = ids[:MaxPeekPanes]
	}
	return ids, linesOut
}

func (s *Service) Peek(ctx context.Context, ids []string, lines int) (wire.PanesPeekResult, error) {
	ids, lines = clampPeek(ids, lines)
	peeks := make([]wire.PanePeek, 0, len(ids))
	for _, id := range ids {
		text, err := s.peekOne(ctx, id, lines)
		if err != nil {
			continue
		}
		peeks = append(peeks, wire.PanePeek{ID: id, Text: text})
	}
	if peeks == nil {
		peeks = []wire.PanePeek{}
	}
	return wire.PanesPeekResult{Peeks: peeks}, nil
}

const peekTimeout = 1500 * time.Millisecond

func (s *Service) peekOne(ctx context.Context, id string, lines int) (string, error) {
	ctx, cancel := context.WithTimeout(ctx, peekTimeout)
	defer cancel()
	runtime := "hc"
	if s.Agents != nil {
		runtime = s.Agents.AgentRuntime(id)
	}
	if runtime == "herdr" || agents.IsHerdrAgentID(id) {
		return s.peekHerdr(ctx, id, lines)
	}
	if s.HC == nil || s.HC.Status() != hc.StatusUp {
		return "", hc.ErrUnavailable
	}
	var out struct {
		Text string `json:"text"`
	}
	params := map[string]any{"terminal": id, "lines": lines}
	if err := s.HC.Call(ctx, "terminal.read", params, &out); err != nil {
		return "", err
	}
	return out.Text, nil
}

func (s *Service) peekHerdr(ctx context.Context, id string, lines int) (string, error) {
	if s.Herdr == nil {
		return "", herdr.ErrUnavailable
	}
	if s.Agents != nil {
		if target, ok := s.Agents.HerdrTarget(id); ok {
			return s.Herdr.AgentRead(ctx, target, "recent_unwrapped", &lines)
		}
	}
	paneID := strings.TrimPrefix(id, agents.HerdrIDPrefix())
	return s.Herdr.PaneRead(ctx, paneID, lines)
}
