package panes

import (
	"context"
	"strings"

	"sh.simple/simpled/internal/agents"
	"sh.simple/simpled/internal/herdr"
	"sh.simple/simpled/internal/wire"
)

func (s *Service) listHerdr(ctx context.Context, workspace string) ([]wire.PaneTab, bool, error) {
	panes, err := s.Herdr.PaneList(ctx)
	if err != nil {
		return nil, false, err
	}
	matched := filterHerdrPanes(panes, workspace)
	if len(matched) == 0 {
		return nil, false, nil
	}
	tabs, err := s.Herdr.TabList(ctx)
	if err != nil {
		return nil, true, err
	}
	tabLabels := map[string]string{}
	tabOrder := []string{}
	seenTab := map[string]bool{}
	for _, tab := range tabs {
		tabLabels[tab.TabID] = tab.Label
	}
	byTab := map[string][]herdr.PaneInfo{}
	for _, p := range matched {
		tid := p.TabID
		byTab[tid] = append(byTab[tid], p)
		if !seenTab[tid] {
			seenTab[tid] = true
			tabOrder = append(tabOrder, tid)
		}
	}
	out := make([]wire.PaneTab, 0, len(tabOrder))
	for _, tid := range tabOrder {
		label := tabLabels[tid]
		if label == "" {
			label = tid
		}
		tabPanes := byTab[tid]
		wpanes := make([]wire.Pane, 0, len(tabPanes))
		for _, p := range tabPanes {
			wpanes = append(wpanes, herdrPane(workspace, p, s.Agents))
		}
		out = append(out, wire.PaneTab{ID: tid, Label: label, Panes: wpanes})
	}
	return out, true, nil
}

func filterHerdrPanes(panes []herdr.PaneInfo, workspace string) []herdr.PaneInfo {
	out := make([]herdr.PaneInfo, 0, len(panes))
	for _, p := range panes {
		cwd := p.Cwd
		if cwd == "" {
			cwd = p.ForegroundCwd
		}
		if workspaceMatchesCwd(workspace, cwd) {
			out = append(out, p)
		}
	}
	return out
}

func herdrPane(workspace string, p herdr.PaneInfo, store *agents.Store) wire.Pane {
	kind := "shell"
	status := "idle"
	id := agents.HerdrAgentID(p.PaneID)
	if p.Agent != "" {
		kind = agents.MapHerdrKind(p.Agent)
		st, _ := agents.MapHerdrStatus(p.AgentStatus)
		status = st
		if store != nil {
			if a, ok := store.Get(id); ok {
				status = a.Status
			}
		}
	} else {
		st, _ := agents.MapHerdrStatus(p.AgentStatus)
		if st != "unknown" {
			status = st
		}
	}
	title := herdrPaneTitle(p)
	tab := p.TabID
	return wire.Pane{
		ID:        id,
		Runtime:   "herdr",
		Workspace: workspace,
		Tab:       &tab,
		Title:     title,
		Kind:      kind,
		Status:    status,
	}
}

func herdrPaneTitle(p herdr.PaneInfo) string {
	title := strings.TrimSpace(p.Name)
	if title == "" {
		title = strings.TrimSpace(p.TerminalTitleStripped)
	}
	if title == "" {
		title = strings.TrimSpace(p.Label)
	}
	if title == "" && p.Agent != "" {
		title = agents.KindLabel(agents.MapHerdrKind(p.Agent))
	}
	if title == "" {
		title = "Terminal"
	}
	return title
}
