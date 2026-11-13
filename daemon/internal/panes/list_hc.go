package panes

import (
	"sh.simple/simpled/internal/wire"
)

func (s *Service) listHC(workspace string) []wire.PaneTab {
	if s.Agents == nil {
		return []wire.PaneTab{{Panes: []wire.Pane{}}}
	}
	var panes []wire.Pane
	for _, a := range s.Agents.Snapshot() {
		if a.Runtime != "hc" || !workspaceMatchesAgent(workspace, a) {
			continue
		}
		panes = append(panes, hcPane(a))
	}
	if panes == nil {
		panes = []wire.Pane{}
	}
	return []wire.PaneTab{{Panes: panes}}
}

func hcPane(a wire.Agent) wire.Pane {
	return wire.Pane{
		ID:        a.ID,
		Runtime:   "hc",
		Workspace: a.Workspace,
		Title:     a.Title,
		Kind:      a.Kind,
		Status:    a.Status,
	}
}
