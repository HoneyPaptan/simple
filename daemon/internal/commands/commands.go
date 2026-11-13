package commands

import (
	"context"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"

	"sh.simple/simpled/internal/opencode"
	"sh.simple/simpled/internal/wire"
)

type Command struct {
	Name        string
	Description string
	Hint        string
}

type cacheEntry struct {
	until   time.Time
	commands []wire.AgentCommand
}

type Service struct {
	oc    *opencode.Client
	mu    sync.Mutex
	cache map[string]cacheEntry
}

func NewService(oc *opencode.Client) *Service {
	return &Service{oc: oc, cache: map[string]cacheEntry{}}
}

func (s *Service) ForAgent(ctx context.Context, a wire.Agent) ([]wire.AgentCommand, error) {
	now := time.Now()
	s.mu.Lock()
	if e, ok := s.cache[a.ID]; ok && now.Before(e.until) {
		out := e.commands
		s.mu.Unlock()
		return out, nil
	}
	s.mu.Unlock()
	var built []Command
	switch a.Kind {
	case "claude":
		built = ClaudeCommands(a.Cwd)
	case "codex":
		built = CodexCommands()
	case "opencode":
		list, _ := s.openCode(ctx)
		built = list
	default:
		built = nil
	}
	out := toWire(built)
	s.mu.Lock()
	s.cache[a.ID] = cacheEntry{until: now.Add(30 * time.Second), commands: out}
	s.mu.Unlock()
	return out, nil
}

func (s *Service) openCode(ctx context.Context) ([]Command, error) {
	if s.oc == nil {
		return nil, nil
	}
	raw, err := s.oc.Commands(ctx)
	if err != nil {
		return nil, err
	}
	out := make([]Command, 0, len(raw))
	for _, c := range raw {
		out = append(out, Command{Name: c.Name, Description: c.Description})
	}
	return out, nil
}

func toWire(in []Command) []wire.AgentCommand {
	if len(in) == 0 {
		return []wire.AgentCommand{}
	}
	out := make([]wire.AgentCommand, 0, len(in))
	seen := map[string]struct{}{}
	for _, c := range in {
		name := strings.TrimSpace(c.Name)
		if name == "" {
			continue
		}
		if _, ok := seen[name]; ok {
			continue
		}
		seen[name] = struct{}{}
		w := wire.AgentCommand{Name: name, Description: strings.TrimSpace(c.Description)}
		if h := strings.TrimSpace(c.Hint); h != "" {
			w.Hint = &h
		}
		out = append(out, w)
	}
	return out
}

func homeDir() string {
	h, _ := os.UserHomeDir()
	return h
}

func claudeCommandRoots(cwd string) []string {
	home := homeDir()
	roots := []string{filepath.Join(home, ".claude", "commands")}
	if cwd != "" {
		roots = append(roots, filepath.Join(cwd, ".claude", "commands"))
	}
	return roots
}

func claudeSkillRoots(cwd string) []string {
	home := homeDir()
	roots := []string{filepath.Join(home, ".claude", "skills")}
	if cwd != "" {
		roots = append(roots, filepath.Join(cwd, ".claude", "skills"))
	}
	return roots
}

func codexPromptDir() string {
	return filepath.Join(homeDir(), ".codex", "prompts")
}
