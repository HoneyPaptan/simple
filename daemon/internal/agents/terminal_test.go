package agents

import "testing"

func TestTerminalLaunchArgv(t *testing.T) {
	if got := TerminalLaunchArgv("gemini", "ask"); len(got) != 2 || got[0] != "gemini" || got[1] != "--skip-trust" {
		t.Fatalf("gemini ask %#v", got)
	}
	full := TerminalLaunchArgv("copilot", "full")
	if len(full) != 2 || full[1] != "--allow-all" {
		t.Fatalf("copilot full %#v", full)
	}
	goose := TerminalLaunchArgv("goose", "ask")
	if len(goose) != 2 || goose[0] != "goose" || goose[1] != "session" {
		t.Fatalf("goose %#v", goose)
	}
	if env := terminalLaunchEnv("goose", "full"); env["GOOSE_MODE"] != "auto" {
		t.Fatalf("goose env %#v", env)
	}
	if len(TerminalLaunchArgv("kimi", "full")) != 2 || TerminalLaunchArgv("kimi", "full")[1] != "--auto" {
		t.Fatalf("kimi %#v", TerminalLaunchArgv("kimi", "full"))
	}
}

func TestLaunchKindsIncludeTerminalAgents(t *testing.T) {
	for _, kind := range []string{"cursor", "gemini", "qwen", "copilot", "amp", "goose", "crush", "aider", "kimi"} {
		if !LaunchKinds[kind] {
			t.Fatalf("missing launch kind %q", kind)
		}
	}
}
