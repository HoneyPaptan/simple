package commands_test

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"testing"

	"sh.simple/simpled/internal/commands"
	"sh.simple/simpled/internal/opencode"
	"sh.simple/simpled/internal/wire"
)

func TestClaudeCommandScan(t *testing.T) {
	root := t.TempDir()
	home := filepath.Join(root, "home")
	cwd := filepath.Join(root, "repo")
	os.Setenv("HOME", home)
	t.Cleanup(func() { os.Unsetenv("HOME") })
	cmdRoot := filepath.Join(home, ".claude", "commands")
	if err := os.MkdirAll(filepath.Join(cmdRoot, "git"), 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(cmdRoot, "review.md"), []byte("---\ndescription: Review changes\n---\n"), 0o644); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(cmdRoot, "git", "commit.md"), []byte("Commit worktrees\n"), 0o644); err != nil {
		t.Fatal(err)
	}
	skillRoot := filepath.Join(cwd, ".claude", "skills", "deploy")
	if err := os.MkdirAll(skillRoot, 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(skillRoot, "SKILL.md"), []byte("---\nname: deploy\ndescription: Ship release\n---\n"), 0o644); err != nil {
		t.Fatal(err)
	}
	got := commands.ClaudeCommands(cwd)
	names := map[string]string{}
	for _, c := range got {
		names[c.Name] = c.Description
	}
	if names["review"] != "Review changes" {
		t.Fatalf("review %q", names["review"])
	}
	if names["git:commit"] != "Commit worktrees" {
		t.Fatalf("git:commit %q", names["git:commit"])
	}
	if names["deploy"] != "Ship release" {
		t.Fatalf("deploy %q", names["deploy"])
	}
}

func TestCodexPromptScan(t *testing.T) {
	root := t.TempDir()
	home := filepath.Join(root, "home")
	os.Setenv("HOME", home)
	t.Cleanup(func() { os.Unsetenv("HOME") })
	dir := filepath.Join(home, ".codex", "prompts")
	if err := os.MkdirAll(dir, 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(dir, "ship.md"), []byte("Release checklist\n"), 0o644); err != nil {
		t.Fatal(err)
	}
	got := commands.CodexCommands()
	var found bool
	for _, c := range got {
		if c.Name == "prompts:ship" && c.Description == "Release checklist" {
			found = true
		}
	}
	if !found {
		t.Fatal("missing prompts:ship")
	}
}

func TestOpenCodeCommands(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/command" {
			http.NotFound(w, r)
			return
		}
		if user, pass, ok := r.BasicAuth(); !ok || user != "opencode" || pass != "secret" {
			http.Error(w, "auth", http.StatusUnauthorized)
			return
		}
		_ = json.NewEncoder(w).Encode(map[string]any{
			"data": []map[string]string{{"name": "init", "description": "init project"}},
		})
	}))
	defer srv.Close()
	base := t.TempDir()
	stateRoot := filepath.Join(base, "state")
	if err := os.MkdirAll(filepath.Join(stateRoot, "opencode"), 0o755); err != nil {
		t.Fatal(err)
	}
	os.Setenv("XDG_STATE_HOME", stateRoot)
	t.Cleanup(func() { os.Unsetenv("XDG_STATE_HOME") })
	state := filepath.Join(stateRoot, "opencode", "service.json")
	if err := os.WriteFile(state, []byte(`{"url":"`+srv.URL+`","password":"secret"}`), 0o600); err != nil {
		t.Fatal(err)
	}
	svc := commands.NewService(opencode.NewClient())
	agent := wire.Agent{ID: "a1", Kind: "opencode", Cwd: t.TempDir()}
	got, err := svc.ForAgent(context.Background(), agent)
	if err != nil {
		t.Fatal(err)
	}
	if len(got) != 1 || got[0].Name != "init" {
		t.Fatalf("%+v", got)
	}
	got2, err := svc.ForAgent(context.Background(), agent)
	if err != nil || len(got2) != 1 {
		t.Fatalf("cache %+v err %v", got2, err)
	}
}
