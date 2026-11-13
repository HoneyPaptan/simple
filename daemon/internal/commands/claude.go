package commands

import (
	"os"
	"path/filepath"
	"strings"
)

func ClaudeCommands(cwd string) []Command {
	out := append([]Command{}, claudeBuiltin...)
	out = append(out, scanMarkdownCommands(claudeCommandRoots(cwd))...)
	out = append(out, scanSkills(claudeSkillRoots(cwd))...)
	return out
}

func scanMarkdownCommands(roots []string) []Command {
	var out []Command
	seen := map[string]struct{}{}
	for _, root := range roots {
		filepath.WalkDir(root, func(path string, d os.DirEntry, err error) error {
			if err != nil {
				return nil
			}
			if d.IsDir() {
				if d.Name() == root || d.Name() == "commands" {
					return nil
				}
				return nil
			}
			if !strings.HasSuffix(strings.ToLower(path), ".md") {
				return nil
			}
			rel, err := filepath.Rel(root, path)
			if err != nil {
				return nil
			}
			rel = strings.TrimSuffix(rel, filepath.Ext(rel))
			rel = filepath.ToSlash(rel)
			name := strings.ReplaceAll(rel, "/", ":")
			if _, ok := seen[name]; ok {
				return nil
			}
			seen[name] = struct{}{}
			body, err := os.ReadFile(path)
			if err != nil {
				return nil
			}
			desc := commandDescription(string(body))
			out = append(out, Command{Name: name, Description: desc})
			return nil
		})
	}
	return out
}

func scanSkills(roots []string) []Command {
	var out []Command
	seen := map[string]struct{}{}
	for _, root := range roots {
		entries, err := os.ReadDir(root)
		if err != nil {
			continue
		}
		for _, e := range entries {
			if !e.IsDir() {
				continue
			}
			path := filepath.Join(root, e.Name(), "SKILL.md")
			body, err := os.ReadFile(path)
			if err != nil {
				continue
			}
			name, desc := skillFrontmatter(string(body))
			if name == "" {
				name = e.Name()
			}
			if _, ok := seen[name]; ok {
				continue
			}
			seen[name] = struct{}{}
			out = append(out, Command{Name: name, Description: desc})
		}
	}
	return out
}

func commandDescription(body string) string {
	if d, ok := frontmatterField(body, "description"); ok && strings.TrimSpace(d) != "" {
		return strings.TrimSpace(d)
	}
	for _, line := range strings.Split(body, "\n") {
		line = strings.TrimSpace(line)
		if line == "" || strings.HasPrefix(line, "---") {
			continue
		}
		if strings.HasPrefix(line, "#") {
			continue
		}
		return line
	}
	return ""
}

func skillFrontmatter(body string) (name, desc string) {
	name, _ = frontmatterField(body, "name")
	desc, _ = frontmatterField(body, "description")
	return strings.TrimSpace(name), strings.TrimSpace(desc)
}

func frontmatterField(body, key string) (string, bool) {
	lines := strings.Split(body, "\n")
	if len(lines) == 0 || strings.TrimSpace(lines[0]) != "---" {
		return "", false
	}
	end := -1
	for i := 1; i < len(lines); i++ {
		if strings.TrimSpace(lines[i]) == "---" {
			end = i
			break
		}
	}
	if end < 0 {
		return "", false
	}
	prefix := key + ":"
	for i := 1; i < end; i++ {
		line := lines[i]
		if !strings.HasPrefix(line, prefix) {
			continue
		}
		val := strings.TrimSpace(strings.TrimPrefix(line, prefix))
		val = strings.Trim(val, `"'`)
		return val, true
	}
	return "", false
}
