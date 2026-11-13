package agents

import (
	"context"
	"encoding/json"
	"os/exec"

	"sh.simple/simpled/internal/hc"
	"sh.simple/simpled/internal/wire"
)

var FxModels = []wire.ModelInfo{
	{ID: "default", Label: "Default", Efforts: []string{"low", "medium", "high"}},
}

var FxModes = []string{}

func FxModelsResult() wire.AgentModelsResult {
	return wire.AgentModelsResult{Models: FxModels, Modes: FxModes}
}

func StartFx(ctx context.Context, client *hc.Client, p StartClaudeParams) (wire.Agent, hc.Terminal, error) {
	cwd, err := ensureWorktree(ctx, client, p.Cwd, worktreeBranch(p.Worktree))
	if err != nil {
		return wire.Agent{}, hc.Terminal{}, err
	}
	argv := []string{"fx"}
	if p.Model != nil && *p.Model != "" && *p.Model != "default" {
		argv = append(argv, "--model", *p.Model)
	}
	if p.Effort != nil && *p.Effort != "" {
		argv = append(argv, "--effort", *p.Effort)
	}
	labels := map[string]string{"simple.kind": "fx"}
	mode := readFxMode()
	if mode != "" {
		labels["simple.mode"] = mode
	}
	if p.Model != nil {
		labels["simple.model"] = *p.Model
	}
	if p.Effort != nil {
		labels["simple.effort"] = *p.Effort
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

func readFxMode() string {
	out, err := exec.Command("fx", "status", "--json").Output()
	if err != nil {
		return ""
	}
	return ParseFxStatusJSON(out)
}

func ParseFxStatusJSON(data []byte) string {
	var st struct {
		Mode string `json:"mode"`
	}
	if json.Unmarshal(data, &st) != nil {
		return ""
	}
	return st.Mode
}
