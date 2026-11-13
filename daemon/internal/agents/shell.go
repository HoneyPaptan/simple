package agents

import (
	"context"
	"fmt"
	"os"
	"path/filepath"
	"runtime"
	"strings"

	"sh.simple/simpled/internal/hc"
	"sh.simple/simpled/internal/wire"
)

func ensureWorktree(ctx context.Context, client *hc.Client, cwd string, branch string) (string, error) {
	if branch == "" {
		return cwd, nil
	}
	root := gitRoot(cwd)
	if root == "" {
		root = cwd
	}
	wt := filepath.Join(root, ".worktrees", branch)
	if st, err := os.Stat(wt); err == nil && st.IsDir() {
		return wt, nil
	}
	addNew := fmt.Sprintf("git worktree add -b %s %s", shellQuote(branch), shellQuote(wt))
	var execRes struct {
		Stdout string `json:"stdout"`
	}
	err := client.OneShotCall(ctx, "machine.exec", map[string]any{"line": addNew, "cwd": root}, &execRes)
	if err != nil {
		addExisting := fmt.Sprintf("git worktree add %s %s", shellQuote(wt), shellQuote(branch))
		if err2 := client.OneShotCall(ctx, "machine.exec", map[string]any{"line": addExisting, "cwd": root}, &execRes); err2 != nil {
			return "", err
		}
	}
	return wt, nil
}

func StartShell(ctx context.Context, client *hc.Client, p StartClaudeParams) (wire.Agent, hc.Terminal, error) {
	cwd, err := ensureWorktree(ctx, client, p.Cwd, worktreeBranch(p.Worktree))
	if err != nil {
		return wire.Agent{}, hc.Terminal{}, err
	}
	argv := shellArgv(runtime.GOOS, os.Getenv)
	labels := map[string]string{"simple.kind": "shell"}
	if p.Mode != "" {
		labels["simple.mode"] = p.Mode
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

func shellArgv(goos string, getenv func(string) string) []string {
	if goos == "windows" {
		return []string{"powershell.exe", "-NoLogo"}
	}
	shell := getenv("SHELL")
	if shell == "" {
		shell = "/bin/bash"
	}
	return []string{shell, "-l"}
}

func worktreeBranch(w *wire.WorktreeParams) string {
	if w == nil {
		return ""
	}
	return strings.TrimSpace(w.Branch)
}
