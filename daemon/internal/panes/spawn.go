package panes

import (
	"context"
	"fmt"

	"sh.simple/simpled/internal/agents"
	"sh.simple/simpled/internal/hc"
)

func (s *Service) Spawn(ctx context.Context, workspace string) (string, error) {
	if workspace == "" {
		return "", fmt.Errorf("missing workspace")
	}
	if s.Herdr != nil {
		_, herdrWS, err := s.listHerdr(ctx, workspace)
		if err != nil {
			return "", err
		}
		if herdrWS {
			var tabRes struct {
				RootPane struct {
					PaneID string `json:"pane_id"`
				} `json:"root_pane"`
			}
			if err := s.Herdr.Call(ctx, "tab.create", map[string]any{
				"cwd":   workspace,
				"label": "Terminal",
				"focus": false,
			}, &tabRes); err != nil {
				return "", err
			}
			if tabRes.RootPane.PaneID == "" {
				return "", fmt.Errorf("herdr tab has no pane")
			}
			return agents.HerdrAgentID(tabRes.RootPane.PaneID), nil
		}
	}
	if s.HC == nil || s.HC.Status() != hc.StatusUp {
		return "", hc.ErrUnavailable
	}
	a, _, err := agents.StartShell(ctx, s.HC, agents.StartClaudeParams{Cwd: workspace})
	if err != nil {
		return "", err
	}
	return a.ID, nil
}
