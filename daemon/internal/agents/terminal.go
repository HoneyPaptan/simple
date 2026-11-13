package agents

import (
	"context"
	"fmt"

	"sh.simple/simpled/internal/hc"
	"sh.simple/simpled/internal/wire"
)

var TerminalModes = []string{"ask", "plan", "auto", "full"}

func TerminalModelsResult() wire.AgentModelsResult {
	return wire.AgentModelsResult{Models: nil, Modes: TerminalModes}
}

var terminalBinaries = map[string]string{
	"gemini":  "gemini",
	"qwen":    "qwen",
	"copilot": "copilot",
	"amp":     "amp",
	"goose":   "goose",
	"crush":   "crush",
	"aider":   "aider",
	"kimi":    "kimi",
}

func IsTerminalLaunchKind(kind string) bool {
	_, ok := terminalBinaries[kind]
	return ok
}

func TerminalLaunchArgv(kind, mode string) []string {
	bin := terminalBinaries[kind]
	if bin == "" {
		return nil
	}
	argv := []string{bin}
	if kind == "goose" {
		argv = []string{bin, "session"}
	}
	argv = append(argv, terminalTrustArgv(kind)...)
	argv = append(argv, terminalFullArgv(kind, mode)...)
	return argv
}

func terminalTrustArgv(kind string) []string {
	switch kind {
	case "gemini":
		return []string{"--skip-trust"}
	default:
		return nil
	}
}

func terminalFullArgv(kind, mode string) []string {
	if mode != "full" {
		return nil
	}
	switch kind {
	case "gemini", "qwen":
		return []string{"--approval-mode", "yolo"}
	case "copilot":
		return []string{"--allow-all"}
	case "amp":
		return []string{"--dangerously-allow-all"}
	case "crush":
		return []string{"--yolo"}
	case "aider":
		return []string{"--yes-always"}
	case "kimi":
		return []string{"--auto"}
	default:
		return nil
	}
}

func terminalLaunchEnv(kind, mode string) map[string]string {
	if mode == "full" && kind == "goose" {
		return map[string]string{"GOOSE_MODE": "auto"}
	}
	return nil
}

func StartTerminalKind(ctx context.Context, client *hc.Client, kind string, p StartClaudeParams) (wire.Agent, hc.Terminal, error) {
	argv := TerminalLaunchArgv(kind, p.Mode)
	if len(argv) == 0 {
		return wire.Agent{}, hc.Terminal{}, fmt.Errorf("unsupported kind %q", kind)
	}
	cwd, err := ensureWorktree(ctx, client, p.Cwd, worktreeBranch(p.Worktree))
	if err != nil {
		return wire.Agent{}, hc.Terminal{}, err
	}
	labels := map[string]string{"simple.kind": kind}
	if p.Mode != "" {
		labels["simple.mode"] = p.Mode
	}
	params := map[string]any{
		"argv":      argv,
		"env":       LaunchEnv(terminalLaunchEnv(kind, p.Mode)),
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
