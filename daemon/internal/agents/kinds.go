package agents

import (
	"sh.simple/simpled/internal/binpath"
	"sh.simple/simpled/internal/wire"
)

var kindBinaries = map[string]string{
	"claude":   "claude",
	"codex":    "codex",
	"cursor":   "cursor-agent",
	"opencode": "opencode",
	"fx":       "fx",
	"gemini":   "gemini",
	"qwen":     "qwen",
	"copilot":  "copilot",
	"amp":      "amp",
	"goose":    "goose",
	"crush":    "crush",
	"aider":    "aider",
	"kimi":     "kimi",
}

func InstalledKinds() wire.AgentKindsResult {
	out := make(map[string]bool, len(kindBinaries)+1)
	for kind, bin := range kindBinaries {
		_, err := binpath.Find(bin)
		out[kind] = err == nil
	}
	out["shell"] = true
	return wire.AgentKindsResult{Kinds: out}
}

var LaunchKinds = map[string]bool{
	"claude":   true,
	"codex":    true,
	"opencode": true,
	"shell":    true,
	"cursor":   true,
	"gemini":   true,
	"qwen":     true,
	"copilot":  true,
	"amp":      true,
	"goose":    true,
	"crush":    true,
	"aider":    true,
	"kimi":     true,
}
