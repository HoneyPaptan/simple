package agents

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"fmt"
	"strings"
	"time"

	"sh.simple/simpled/internal/herdr"
	"sh.simple/simpled/internal/opencode"
	"sh.simple/simpled/internal/wire"
)

func opencodeSessionLabel() string {
	return opencode.NewSessionID()
}

func HerdrLaunchKind(kind string) string {
	switch kind {
	case "claude", "codex", "cursor", "opencode", "fx", "shell":
		return kind
	default:
		if IsTerminalLaunchKind(kind) {
			return "shell"
		}
		return ""
	}
}

func HerdrLaunchArgs(kind string, p StartClaudeParams) []string {
	switch kind {
	case "claude":
		var argv []string
		if p.Mode != "" {
			argv = append(argv, "--permission-mode", SimpleModeToClaude(p.Mode))
		}
		if p.Model != nil && *p.Model != "" && *p.Model != "default" {
			argv = append(argv, "--model", *p.Model)
		}
		if p.Effort != nil && *p.Effort != "" {
			argv = append(argv, "--effort", *p.Effort)
		}
		return argv
	case "codex":
		approval, sandbox, extra := SimpleModeToCodexFlags(p.Mode)
		var argv []string
		if p.Model != nil && *p.Model != "" && *p.Model != "default" {
			argv = append(argv, "-m", *p.Model)
		}
		if len(extra) > 0 {
			argv = append(argv, extra...)
		} else {
			argv = append(argv, "--ask-for-approval", approval, "--sandbox", sandbox)
		}
		return argv
	case "cursor":
		argv := append([]string{}, SimpleModeToCursor(p.Mode)...)
		if p.Model != nil && *p.Model != "" && *p.Model != "default" {
			argv = append(argv, "--model", *p.Model)
		}
		return argv
	case "opencode":
		session := opencodeSessionLabel()
		return []string{"--session", session, p.Cwd}
	case "fx":
		var argv []string
		if p.Model != nil && *p.Model != "" && *p.Model != "default" {
			argv = append(argv, "--model", *p.Model)
		}
		if p.Effort != nil && *p.Effort != "" {
			argv = append(argv, "--effort", *p.Effort)
		}
		return argv
	case "shell":
		return nil
	default:
		if IsTerminalLaunchKind(kind) {
			return TerminalLaunchArgv(kind, p.Mode)
		}
		return nil
	}
}

func StartInHerdr(ctx context.Context, client *herdr.Client, kind string, p StartClaudeParams) (wire.Agent, error) {
	hkind := HerdrLaunchKind(kind)
	if hkind == "" {
		return wire.Agent{}, fmt.Errorf("unsupported kind %q", kind)
	}
	cwd := p.Cwd
	if cwd == "" {
		return wire.Agent{}, fmt.Errorf("missing cwd")
	}
	label := KindLabel(kind)
	var tabRes struct {
		RootPane struct {
			PaneID string `json:"pane_id"`
		} `json:"root_pane"`
	}
	if err := client.Call(ctx, "tab.create", map[string]any{
		"cwd":   cwd,
		"label": label,
		"focus": false,
	}, &tabRes); err != nil {
		return wire.Agent{}, err
	}
	paneID := tabRes.RootPane.PaneID
	if paneID == "" {
		return wire.Agent{}, fmt.Errorf("herdr tab has no pane")
	}
	name, err := herdrAgentName()
	if err != nil {
		return wire.Agent{}, err
	}
	params := map[string]any{
		"name":    name,
		"kind":    hkind,
		"pane_id": paneID,
	}
	args := HerdrLaunchArgs(kind, p)
	if len(args) > 0 {
		params["args"] = args
	}
	if err := startWhenShellReady(ctx, client, params); err != nil {
		return wire.Agent{}, err
	}
	if p.Prompt != nil && strings.TrimSpace(*p.Prompt) != "" {
		go promptWhenReady(client, name, *p.Prompt)
	}
	list, err := client.AgentList(ctx)
	if err != nil {
		return wire.Agent{}, err
	}
	session := sessionArg(args)
	for _, info := range list {
		if info.PaneID == paneID {
			a := buildHerdrAgent(info)
			if a.Session == nil && session != nil {
				a.Session = session
			}
			return a, nil
		}
	}
	return wire.Agent{
		ID:        HerdrAgentID(paneID),
		Runtime:   "herdr",
		Kind:      kind,
		Title:     label,
		Cwd:       cwd,
		Workspace: gitRoot(cwd),
		Status:    "working",
		Session:   session,
	}, nil
}

func sessionArg(args []string) *string {
	for i := 0; i+1 < len(args); i++ {
		if args[i] == "--session" {
			v := args[i+1]
			return &v
		}
	}
	return nil
}

func herdrAgentName() (string, error) {
	var b [4]byte
	if _, err := rand.Read(b[:]); err != nil {
		return "", err
	}
	return "s" + strings.ToLower(hex.EncodeToString(b[:])), nil
}

const herdrShellWait = 8 * time.Second

func startWhenShellReady(ctx context.Context, client *herdr.Client, params map[string]any) error {
	deadline := time.Now().Add(herdrShellWait)
	for {
		err := client.Call(ctx, "agent.start", params, nil)
		if err == nil || !strings.Contains(err.Error(), "not an available shell") || time.Now().After(deadline) {
			return err
		}
		select {
		case <-ctx.Done():
			return ctx.Err()
		case <-time.After(200 * time.Millisecond):
		}
	}
}

const (
	herdrReadyWait = 90 * time.Second
	herdrSettle    = 1500 * time.Millisecond
	herdrTurnStart = 5 * time.Second
)

func promptWhenReady(client *herdr.Client, name, text string) {
	ctx, cancel := context.WithTimeout(context.Background(), herdrReadyWait)
	defer cancel()
	for {
		if herdrAgentReady(ctx, client, name) {
			time.Sleep(herdrSettle)
			if client.AgentPrompt(ctx, name, text) == nil {
				nudgeIfIdle(ctx, client, name)
			}
			return
		}
		select {
		case <-ctx.Done():
			return
		case <-time.After(300 * time.Millisecond):
		}
	}
}

func herdrAgentReady(ctx context.Context, client *herdr.Client, name string) bool {
	list, err := client.AgentList(ctx)
	if err != nil {
		return false
	}
	for _, info := range list {
		if info.Name == name {
			return info.InteractiveReady && (info.AgentStatus == "idle" || info.AgentStatus == "done")
		}
	}
	return false
}

func nudgeIfIdle(ctx context.Context, client *herdr.Client, name string) {
	deadline := time.Now().Add(herdrTurnStart)
	for time.Now().Before(deadline) {
		if st := herdrAgentStatus(ctx, client, name); st != "idle" && st != "" {
			return
		}
		time.Sleep(300 * time.Millisecond)
	}
	_ = client.AgentSendKeys(ctx, name, []string{"enter"})
}

func herdrAgentStatus(ctx context.Context, client *herdr.Client, name string) string {
	list, err := client.AgentList(ctx)
	if err != nil {
		return ""
	}
	for _, info := range list {
		if info.Name == name {
			return info.AgentStatus
		}
	}
	return ""
}
