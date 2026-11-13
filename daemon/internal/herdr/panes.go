package herdr

import (
	"context"
	"strings"
)

type TabInfo struct {
	TabID       string `json:"tab_id"`
	WorkspaceID string `json:"workspace_id"`
	Number      int    `json:"number"`
	Label       string `json:"label"`
	Focused     bool   `json:"focused"`
	PaneCount   int    `json:"pane_count"`
	AgentStatus string `json:"agent_status"`
}

type TabListResult struct {
	Type string    `json:"type"`
	Tabs []TabInfo `json:"tabs"`
}

type PaneInfo struct {
	PaneID                string `json:"pane_id"`
	TerminalID            string `json:"terminal_id"`
	WorkspaceID           string `json:"workspace_id"`
	TabID                 string `json:"tab_id"`
	Cwd                   string `json:"cwd"`
	ForegroundCwd         string `json:"foreground_cwd"`
	Label                 string `json:"label"`
	Agent                 string `json:"agent"`
	TerminalTitleStripped string `json:"terminal_title_stripped"`
	AgentStatus           string `json:"agent_status"`
	Name                  string `json:"name"`
}

type PaneListResult struct {
	Type  string     `json:"type"`
	Panes []PaneInfo `json:"panes"`
}

type PaneReadParams struct {
	PaneID    string `json:"pane_id"`
	Source    string `json:"source"`
	Format    string `json:"format"`
	StripAnsi bool   `json:"strip_ansi"`
	Lines     int    `json:"lines,omitempty"`
}

type PaneReadResult struct {
	Type string   `json:"type"`
	Read PaneRead `json:"read"`
}

type PaneGetResult struct {
	Type string   `json:"type"`
	Pane PaneInfo `json:"pane"`
}

func (c *Client) TabList(ctx context.Context) ([]TabInfo, error) {
	var res TabListResult
	if err := c.Call(ctx, "tab.list", map[string]any{}, &res); err != nil {
		return nil, err
	}
	return res.Tabs, nil
}

func (c *Client) PaneList(ctx context.Context) ([]PaneInfo, error) {
	var res PaneListResult
	if err := c.Call(ctx, "pane.list", map[string]any{}, &res); err != nil {
		return nil, err
	}
	for i := range res.Panes {
		res.Panes[i].cleanCwds()
	}
	return res.Panes, nil
}

func (c *Client) PaneRead(ctx context.Context, paneID string, lines int) (string, error) {
	var res PaneReadResult
	params := map[string]any{
		"pane_id":    paneID,
		"source":     "recent_unwrapped",
		"format":     "text",
		"strip_ansi": true,
	}
	if lines > 0 {
		params["lines"] = lines
	}
	if err := c.Call(ctx, "pane.read", params, &res); err != nil {
		return "", err
	}
	return res.Read.Text, nil
}

func (c *Client) PaneGet(ctx context.Context, paneID string) (PaneInfo, error) {
	var res PaneGetResult
	if err := c.Call(ctx, "pane.get", map[string]any{"pane_id": paneID}, &res); err != nil {
		return PaneInfo{}, err
	}
	res.Pane.cleanCwds()
	return res.Pane, nil
}

func (p *PaneInfo) cleanCwds() {
	p.Cwd = CleanCwd(p.Cwd)
	p.ForegroundCwd = CleanCwd(p.ForegroundCwd)
}

func CleanCwd(raw string) string {
	return strings.TrimSuffix(strings.TrimSpace(raw), " (deleted)")
}

func (c *Client) PaneTerminalID(ctx context.Context, paneID string) (string, error) {
	p, err := c.PaneGet(ctx, paneID)
	if err != nil {
		return "", err
	}
	return p.TerminalID, nil
}
