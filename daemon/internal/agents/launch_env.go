package agents

import (
	"os"
	"path/filepath"
)

var inheritedSessionVars = []string{
	"CLAUDECODE",
	"CLAUDE_CODE_CHILD_SESSION",
	"CLAUDE_CODE_ENTRYPOINT",
	"CLAUDE_CODE_EXECPATH",
	"CLAUDE_CODE_MESSAGING_SOCKET",
	"CLAUDE_CODE_MESSAGING_TOKEN",
	"CLAUDE_CODE_PLUGIN_DIRS",
	"CLAUDE_CODE_SESSION_ATTENDED",
	"CLAUDE_CODE_SESSION_ID",
	"CLAUDE_PID",
	"CLAUDE_EFFORT",
}

func LaunchEnv(extra map[string]string) map[string]string {
	env := map[string]string{}
	for _, name := range inheritedSessionVars {
		env[name] = ""
	}
	if dir := os.Getenv("CLAUDE_CONFIG_DIR"); dir != "" {
		env["CLAUDE_CONFIG_DIR"] = dir
	}
	for k, v := range extra {
		env[k] = v
	}
	return env
}

func ClaudeConfigDir() string {
	if dir := os.Getenv("CLAUDE_CONFIG_DIR"); dir != "" {
		return dir
	}
	home, err := os.UserHomeDir()
	if err != nil {
		return ""
	}
	return filepath.Join(home, ".claude")
}
