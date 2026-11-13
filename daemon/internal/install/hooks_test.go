package install_test

import (
	"encoding/json"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"sh.simple/simpled/internal/install"
)

func TestHooksInstallMergeAndUninstall(t *testing.T) {
	home := t.TempDir()
	t.Setenv("HOME", home)
	simpled := filepath.Join(home, "bin", "simpled")
	if err := os.MkdirAll(filepath.Dir(simpled), 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(simpled, []byte("#!/bin/sh\n"), 0o755); err != nil {
		t.Fatal(err)
	}
	codexDir := filepath.Join(home, ".codex")
	if err := os.MkdirAll(codexDir, 0o755); err != nil {
		t.Fatal(err)
	}
	third := `{"extra":true,"hooks":{"SessionStart":[{"matcher":"","hooks":[{"type":"command","command":"echo keep","commandWindows":"echo win","async":true,"timeout":5}]}]}}`
	if err := os.WriteFile(filepath.Join(codexDir, "hooks.json"), []byte(third), 0o644); err != nil {
		t.Fatal(err)
	}
	cursorDir := filepath.Join(home, ".cursor")
	if err := os.MkdirAll(cursorDir, 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(cursorDir, "hooks.json"), []byte(`{"version":1,"hooks":{"sessionStart":[{"command":"other hook","timeout":1}]}}`), 0o644); err != nil {
		t.Fatal(err)
	}
	if err := install.InstallHooks(simpled); err != nil {
		t.Fatal(err)
	}
	if err := install.InstallHooks(simpled); err != nil {
		t.Fatal(err)
	}
	codexRaw, err := os.ReadFile(filepath.Join(codexDir, "hooks.json"))
	if err != nil {
		t.Fatal(err)
	}
	for _, want := range []string{"echo keep", "commandWindows", `"async": true`, `"extra": true`} {
		if !strings.Contains(string(codexRaw), want) {
			t.Fatalf("lost third party codex field %s", want)
		}
	}
	if strings.Count(string(codexRaw), "' hook codex ") != 5 {
		t.Fatalf("codex hook count %d", strings.Count(string(codexRaw), "' hook codex "))
	}
	if _, err := os.Stat(filepath.Join(codexDir, "hooks.json.bak")); err != nil {
		t.Fatal("expected codex bak")
	}
	cursorRaw, err := os.ReadFile(filepath.Join(cursorDir, "hooks.json"))
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(string(cursorRaw), "other hook") {
		t.Fatal("lost third party cursor hook")
	}
	if strings.Count(string(cursorRaw), "' hook cursor ") != 7 {
		t.Fatalf("cursor hook count %d", strings.Count(string(cursorRaw), "' hook cursor "))
	}
	pluginPath := filepath.Join(home, ".config", "opencode", "plugins", "simple.ts")
	plugin, err := os.ReadFile(pluginPath)
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(string(plugin), simpled) || !strings.Contains(string(plugin), "simpled hook") {
		t.Fatal("plugin content")
	}
	if !strings.Contains(string(plugin), "permission.ask") {
		t.Fatal("plugin permission ask hook")
	}
	if !strings.Contains(string(plugin), "postSessionIdPermissionsPermissionId") {
		t.Fatal("plugin permission fallback")
	}
	if err := install.UninstallHooks(); err != nil {
		t.Fatal(err)
	}
	codexRaw, _ = os.ReadFile(filepath.Join(codexDir, "hooks.json"))
	var codexRoot map[string]any
	_ = json.Unmarshal(codexRaw, &codexRoot)
	hooks, _ := codexRoot["hooks"].(map[string]any)
	if hooks == nil || len(hooks) != 1 {
		t.Fatalf("codex after uninstall %#v", codexRoot)
	}
	if _, err := os.Stat(pluginPath); !os.IsNotExist(err) {
		t.Fatal("plugin should be removed")
	}
	cursorRaw, _ = os.ReadFile(filepath.Join(cursorDir, "hooks.json"))
	if !strings.Contains(string(cursorRaw), "other hook") || strings.Contains(string(cursorRaw), "' hook cursor ") {
		t.Fatal("cursor after uninstall")
	}
}

func TestHooksInstallLeavesInvalidJSONAlone(t *testing.T) {
	home := t.TempDir()
	t.Setenv("HOME", home)
	path := filepath.Join(home, ".codex", "hooks.json")
	if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
		t.Fatal(err)
	}
	broken := []byte(`{"hooks": {`)
	if err := os.WriteFile(path, broken, 0o644); err != nil {
		t.Fatal(err)
	}
	if err := install.InstallHooks("/opt/simpled"); err == nil {
		t.Fatal("expected an error for invalid JSON")
	}
	got, _ := os.ReadFile(path)
	if string(got) != string(broken) {
		t.Fatalf("file changed: %s", got)
	}
}
