package agents

import "testing"

func TestLaunchEnvClearsInheritedSessionAndUsesOwnLogin(t *testing.T) {
	t.Setenv("CLAUDE_CONFIG_DIR", "")
	t.Setenv("HOME", "/home/u")
	env := LaunchEnv(nil)
	if v, ok := env["CLAUDE_CODE_CHILD_SESSION"]; !ok || v != "" {
		t.Fatalf("child session %q %v", v, ok)
	}
	if v, ok := env["CLAUDE_CONFIG_DIR"]; ok {
		t.Fatalf("config dir must stay unset, got %q", v)
	}
	t.Setenv("CLAUDE_CONFIG_DIR", "/home/u/.claude-work")
	env = LaunchEnv(map[string]string{"OPENCODE_CONFIG_DIR": "/x"})
	if env["CLAUDE_CONFIG_DIR"] != "/home/u/.claude-work" || env["OPENCODE_CONFIG_DIR"] != "/x" {
		t.Fatalf("env %v", env)
	}
}

func TestPaneNotReadyOnClaudeTrustMenu(t *testing.T) {
	screen := " Quick safety check: Is this a project you created or one you trust?\n\n ❯ No, exit\n   Yes, I trust this folder\n"
	if paneReady("claude", screen) {
		t.Fatal("ready on trust menu")
	}
}
