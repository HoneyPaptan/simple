package agents

import (
	"context"

	"sh.simple/simpled/internal/hc"
	"sh.simple/simpled/internal/opencode"
	"sh.simple/simpled/internal/wire"
)

var OpenCodeModels = []wire.ModelInfo{
	{ID: "default", Label: "Default", Efforts: []string{}},
}

var OpenCodeModes = []string{"ask", "auto", "full"}

func OpenCodeModelsResult() wire.AgentModelsResult {
	return wire.AgentModelsResult{Models: OpenCodeModels, Modes: OpenCodeModes}
}

func StartOpenCode(ctx context.Context, client *hc.Client, p StartClaudeParams) (wire.Agent, hc.Terminal, error) {
	cwd, err := ensureWorktree(ctx, client, p.Cwd, worktreeBranch(p.Worktree))
	if err != nil {
		return wire.Agent{}, hc.Terminal{}, err
	}
	session := opencode.NewSessionID()
	argv := []string{"opencode", "--session", session}
	argv = append(argv, cwd)
	labels := map[string]string{"simple.kind": "opencode", "simple.mode": p.Mode, "simple.session": session}
	if p.Model != nil {
		labels["simple.model"] = *p.Model
	}
	params := map[string]any{
		"argv":      argv,
		"env":       LaunchEnv(nil),
		"cwd":       cwd,
		"labels":    labels,
		"no_parent": true,
		"wait":      false,
	}
	var term hc.Terminal
	if err := client.Call(ctx, "terminal.run", params, &term); err != nil {
		return wire.Agent{}, hc.Terminal{}, err
	}
	if term.Labels == nil {
		term.Labels = labels
	}
	a := buildAgentStatic(term)
	return a, term, nil
}
