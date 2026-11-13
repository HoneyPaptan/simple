package agents

import (
	"context"
	"crypto/rand"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"sync"
	"time"

	"sh.simple/simpled/internal/hc"
	"sh.simple/simpled/internal/hookcmd"
	"sh.simple/simpled/internal/wire"
)

var ClaudeModels = []wire.ModelInfo{
	{ID: "default", Label: "Default", Efforts: []string{"low", "medium", "high", "max"}},
	{ID: "opus", Label: "Opus", Efforts: []string{"low", "medium", "high", "max"}},
	{ID: "sonnet", Label: "Sonnet", Efforts: []string{"low", "medium", "high", "max"}},
	{ID: "haiku", Label: "Haiku", Efforts: []string{"low", "medium", "high"}},
	{ID: "fable", Label: "Fable", Efforts: []string{"low", "medium", "high"}},
}

var ClaudeModes = []string{"ask", "plan", "auto", "full"}

func ClaudeModelsResult() wire.AgentModelsResult {
	return wire.AgentModelsResult{Models: ClaudeModels, Modes: ClaudeModes}
}

func SimpleModeToClaude(mode string) string {
	switch strings.ToLower(mode) {
	case "ask":
		return "default"
	case "plan":
		return "plan"
	case "auto":
		return "auto"
	case "full":
		return "bypassPermissions"
	default:
		return "default"
	}
}

func ClaudeSettingsJSON(simpledPath string) string {
	return claudeSettingsJSON(runtime.GOOS, simpledPath)
}

func claudeSettingsJSON(goos, simpledPath string) string {
	type hookDef struct {
		Type    string `json:"type"`
		Command string `json:"command"`
		Timeout int    `json:"timeout"`
	}
	build := func(event string, timeout int, matcher string) []map[string]any {
		entry := map[string]any{
			"hooks": []hookDef{{Type: "command", Command: hookcmd.Line(goos, simpledPath, "claude", event), Timeout: timeout}},
		}
		if matcher != "" {
			entry["matcher"] = matcher
		}
		return []map[string]any{entry}
	}
	hooks := map[string]any{
		"SessionStart":      build("SessionStart", 30, ""),
		"PermissionRequest": build("PermissionRequest", 600, ""),
		"PreToolUse":        build("PreToolUse", 600, "AskUserQuestion"),
		"Notification":      build("Notification", 30, ""),
		"Stop":              build("Stop", 30, ""),
		"UserPromptSubmit":  build("UserPromptSubmit", 30, ""),
		"SessionEnd":        build("SessionEnd", 30, ""),
	}
	settings := map[string]any{"hooks": hooks}
	b, _ := json.Marshal(settings)
	return string(b)
}

type StartClaudeParams struct {
	Cwd      string
	Prompt   *string
	Model    *string
	Effort   *string
	Mode     string
	Worktree *wire.WorktreeParams
}

func StartClaude(ctx context.Context, client *hc.Client, simpledPath string, p StartClaudeParams) (wire.Agent, hc.Terminal, error) {
	cwd, err := ensureWorktree(ctx, client, p.Cwd, worktreeBranch(p.Worktree))
	if err != nil {
		return wire.Agent{}, hc.Terminal{}, err
	}
	session := newUUID()
	argv := []string{"claude", "--session-id", session, "--permission-mode", SimpleModeToClaude(p.Mode)}
	if p.Model != nil && *p.Model != "" {
		argv = append(argv, "--model", *p.Model)
	}
	if p.Effort != nil && *p.Effort != "" {
		argv = append(argv, "--effort", *p.Effort)
	}
	argv = append(argv, "--settings", ClaudeSettingsJSON(simpledPath))
	labels := map[string]string{
		"simple.kind":    "claude",
		"simple.session": session,
		"simple.mode":    p.Mode,
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

func buildAgentStatic(t hc.Terminal) wire.Agent {
	s := &Store{}
	return s.buildAgent(t)
}

func shellQuote(s string) string {
	return "'" + strings.ReplaceAll(s, "'", "'\\''") + "'"
}

func ClaudeProjectDir(cwd string) string {
	return filepath.Join(ClaudeConfigDir(), "projects", projectSlug(cwd))
}

func projectSlug(cwd string) string {
	var b strings.Builder
	for _, r := range cwd {
		if (r >= 'a' && r <= 'z') || (r >= 'A' && r <= 'Z') || (r >= '0' && r <= '9') {
			b.WriteRune(r)
		} else {
			b.WriteByte('-')
		}
	}
	return b.String()
}

func TranscriptPath(session, cwd string) string {
	fallback := filepath.Join(ClaudeProjectDir(cwd), session+".jsonl")
	if session == "" || strings.ContainsAny(session, `/\*?[`) {
		return fallback
	}
	if _, err := os.Stat(fallback); err == nil {
		return fallback
	}
	if hit, ok := transcriptHits.get(session); ok {
		if hit == "" {
			return fallback
		}
		if _, err := os.Stat(hit); err == nil {
			return hit
		}
	}
	found := ""
	for _, dir := range claudeConfigDirs() {
		matches, _ := filepath.Glob(filepath.Join(dir, "projects", "*", session+".jsonl"))
		if len(matches) > 0 {
			found = matches[0]
			break
		}
	}
	transcriptHits.put(session, found)
	if found == "" {
		return fallback
	}
	return found
}

const transcriptMissTTL = 10 * time.Second

type transcriptHit struct {
	path string
	at   time.Time
}

type transcriptHitCache struct {
	mu sync.Mutex
	m  map[string]transcriptHit
}

var transcriptHits = &transcriptHitCache{m: map[string]transcriptHit{}}

func (c *transcriptHitCache) get(session string) (string, bool) {
	c.mu.Lock()
	defer c.mu.Unlock()
	h, ok := c.m[session]
	if !ok || (h.path == "" && time.Since(h.at) > transcriptMissTTL) {
		return "", false
	}
	return h.path, true
}

func (c *transcriptHitCache) put(session, path string) {
	c.mu.Lock()
	defer c.mu.Unlock()
	if len(c.m) > 512 {
		c.m = map[string]transcriptHit{}
	}
	c.m[session] = transcriptHit{path: path, at: time.Now()}
}

func claudeConfigDirs() []string {
	seen := map[string]bool{}
	var dirs []string
	add := func(d string) {
		if d != "" && !seen[d] {
			seen[d] = true
			dirs = append(dirs, d)
		}
	}
	add(ClaudeConfigDir())
	if home, err := os.UserHomeDir(); err == nil {
		add(filepath.Join(home, ".claude"))
		more, _ := filepath.Glob(filepath.Join(home, ".claude-*"))
		for _, d := range more {
			if st, err := os.Stat(d); err == nil && st.IsDir() {
				add(d)
			}
		}
	}
	return dirs
}

func newUUID() string {
	var b [16]byte
	rand.Read(b[:])
	b[6] = (b[6] & 0x0f) | 0x40
	b[8] = (b[8] & 0x3f) | 0x80
	return fmt.Sprintf("%x-%x-%x-%x-%x", b[0:4], b[4:6], b[6:8], b[8:10], b[10:16])
}
