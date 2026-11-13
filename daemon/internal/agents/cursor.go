package agents

import (
	"context"
	"os/exec"
	"strings"
	"sync"
	"time"

	"sh.simple/simpled/internal/hc"
	"sh.simple/simpled/internal/wire"
)

var CursorModels = []wire.ModelInfo{
	{ID: "default", Label: "Default", Efforts: []string{}},
}

var CursorModes = []string{"ask", "plan", "auto", "full"}

var cursorCache struct {
	mu   sync.Mutex
	at   time.Time
	data wire.AgentModelsResult
}

func CursorModelsResult() wire.AgentModelsResult {
	cursorCache.mu.Lock()
	defer cursorCache.mu.Unlock()
	if time.Since(cursorCache.at) < time.Hour && len(cursorCache.data.Models) > 0 {
		return cursorCache.data
	}
	out := wire.AgentModelsResult{Models: CursorModels, Modes: CursorModes}
	cmd := exec.Command("cursor-agent", "--list-models")
	b, err := cmd.Output()
	if err == nil {
		if models := ParseCursorModelList(string(b)); len(models) > 0 {
			out.Models = append([]wire.ModelInfo{{ID: "default", Label: "Default", Efforts: []string{}}}, models...)
		}
	}
	cursorCache.data = out
	cursorCache.at = time.Now()
	return out
}

func ParseCursorModelList(text string) []wire.ModelInfo {
	var models []wire.ModelInfo
	for _, line := range strings.Split(text, "\n") {
		line = strings.TrimSpace(line)
		if line == "" || strings.HasPrefix(line, "Available") {
			continue
		}
		id, label, ok := strings.Cut(line, " - ")
		if !ok {
			continue
		}
		id = strings.TrimSpace(id)
		label = strings.TrimSpace(label)
		label = strings.TrimSuffix(label, " (current)")
		if id == "" {
			continue
		}
		if label == "" {
			label = id
		}
		models = append(models, wire.ModelInfo{ID: id, Label: label, Efforts: []string{}})
	}
	return models
}

func SimpleModeToCursor(mode string) []string {
	switch mode {
	case "plan":
		return []string{"--plan"}
	case "auto":
		return []string{"--auto-review"}
	case "full":
		return []string{"--force"}
	default:
		return nil
	}
}

func StartCursor(ctx context.Context, client *hc.Client, p StartClaudeParams) (wire.Agent, hc.Terminal, error) {
	cwd, err := ensureWorktree(ctx, client, p.Cwd, worktreeBranch(p.Worktree))
	if err != nil {
		return wire.Agent{}, hc.Terminal{}, err
	}
	argv := []string{"cursor-agent", "--trust"}
	argv = append(argv, SimpleModeToCursor(p.Mode)...)
	if p.Model != nil && *p.Model != "" && *p.Model != "default" {
		argv = append(argv, "--model", *p.Model)
	}
	labels := map[string]string{"simple.kind": "cursor", "simple.mode": p.Mode}
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
