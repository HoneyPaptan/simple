package agents

import (
	"context"

	"sh.simple/simpled/internal/hc"
	"sh.simple/simpled/internal/wire"
)

var CodexModels = []wire.ModelInfo{
	{ID: "default", Label: "Default", Efforts: []string{}},
	{ID: "gpt-5-codex", Label: "GPT-5 Codex", Efforts: []string{}},
	{ID: "o4-mini", Label: "o4-mini", Efforts: []string{}},
}

var CodexModes = []string{"ask", "auto", "full"}

func CodexModelsResult() wire.AgentModelsResult {
	return wire.AgentModelsResult{Models: CodexModels, Modes: CodexModes}
}

func SimpleModeToCodexFlags(mode string) (approval string, sandbox string, extra []string) {
	switch mode {
	case "auto":
		return "on-request", "workspace-write", []string{"--approve-for-me"}
	case "full":
		return "", "", []string{"--dangerously-bypass-approvals-and-sandbox"}
	default:
		return "on-request", "workspace-write", nil
	}
}

func StartCodex(ctx context.Context, client *hc.Client, p StartClaudeParams) (wire.Agent, hc.Terminal, error) {
	cwd, err := ensureWorktree(ctx, client, p.Cwd, worktreeBranch(p.Worktree))
	if err != nil {
		return wire.Agent{}, hc.Terminal{}, err
	}
	approval, sandbox, extra := SimpleModeToCodexFlags(p.Mode)
	argv := []string{"codex"}
	if p.Model != nil && *p.Model != "" && *p.Model != "default" {
		argv = append(argv, "-m", *p.Model)
	}
	if len(extra) > 0 {
		argv = append(argv, extra...)
	} else {
		argv = append(argv, "--ask-for-approval", approval, "--sandbox", sandbox)
	}
	labels := map[string]string{"simple.kind": "codex", "simple.mode": p.Mode}
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
