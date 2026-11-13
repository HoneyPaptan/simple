package commands

import (
	"os"
	"path/filepath"
	"strings"
)

func CodexCommands() []Command {
	out := append([]Command{}, codexBuiltin...)
	dir := codexPromptDir()
	entries, err := os.ReadDir(dir)
	if err != nil {
		return out
	}
	for _, e := range entries {
		if e.IsDir() {
			continue
		}
		if !strings.HasSuffix(strings.ToLower(e.Name()), ".md") {
			continue
		}
		base := strings.TrimSuffix(e.Name(), filepath.Ext(e.Name()))
		body, err := os.ReadFile(filepath.Join(dir, e.Name()))
		if err != nil {
			continue
		}
		desc := commandDescription(string(body))
		out = append(out, Command{
			Name:        "prompts:" + base,
			Description: desc,
		})
	}
	return out
}
