package panes

import (
	"context"
	"os"
	"path/filepath"
	"strings"

	"sh.simple/simpled/internal/agents"
	"sh.simple/simpled/internal/hc"
	"sh.simple/simpled/internal/herdr"
	"sh.simple/simpled/internal/wire"
)

const (
	MaxPeekPanes = 8
	MaxPeekLines = 20
)

type Service struct {
	Agents *agents.Store
	HC     *hc.Client
	Herdr  *herdr.Client
}

func New(agents *agents.Store, hcClient *hc.Client, herdrClient *herdr.Client) *Service {
	return &Service{Agents: agents, HC: hcClient, Herdr: herdrClient}
}

func workspaceMatchesAgent(workspace string, a wire.Agent) bool {
	if workspace == "" {
		return false
	}
	if a.Workspace == workspace {
		return true
	}
	return pathHasPrefix(a.Cwd, workspace)
}

func workspaceMatchesCwd(workspace, cwd string) bool {
	if workspace == "" {
		return false
	}
	root := workspaceRoot(cwd)
	if root == workspace {
		return true
	}
	return pathHasPrefix(cwd, workspace)
}

func pathHasPrefix(child, root string) bool {
	if root == "" {
		return child == ""
	}
	if child == root {
		return true
	}
	if !strings.HasPrefix(child, root) {
		return false
	}
	if len(child) == len(root) {
		return true
	}
	return child[len(root)] == filepath.Separator
}

func workspaceRoot(cwd string) string {
	if cwd == "" {
		return ""
	}
	dir, err := filepath.Abs(cwd)
	if err != nil {
		return cwd
	}
	for {
		if _, err := os.Stat(filepath.Join(dir, ".git")); err == nil {
			return dir
		}
		parent := filepath.Dir(dir)
		if parent == dir {
			return cwd
		}
		dir = parent
	}
}

func (s *Service) List(ctx context.Context, workspace string) (wire.PanesListResult, error) {
	if workspace == "" {
		return wire.PanesListResult{}, nil
	}
	if s.Herdr != nil {
		tabs, ok, err := s.listHerdr(ctx, workspace)
		if err == nil && ok {
			return wire.PanesListResult{Tabs: tabs}, nil
		}
	}
	return wire.PanesListResult{Tabs: s.listHC(workspace)}, nil
}
