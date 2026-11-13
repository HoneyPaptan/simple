package api

import (
	"context"
	"encoding/json"
	"os"
	"os/exec"
	"path/filepath"
	"testing"

	"sh.simple/simpled/internal/wire"
)

func TestDispatchGitMethods(t *testing.T) {
	root := t.TempDir()
	for _, args := range [][]string{
		{"init"},
		{"config", "user.email", "t@t.com"},
		{"config", "user.name", "t"},
	} {
		cmd := exec.Command("git", args...)
		cmd.Dir = root
		if err := cmd.Run(); err != nil {
			t.Fatal(err)
		}
	}
	os.WriteFile(filepath.Join(root, "a.txt"), []byte("a"), 0o644)
	cmd := exec.Command("git", "add", "a.txt")
	cmd.Dir = root
	cmd.Run()
	cmd = exec.Command("git", "commit", "-m", "init")
	cmd.Dir = root
	cmd.Run()
	os.WriteFile(filepath.Join(root, "b.txt"), []byte("b"), 0o644)

	srv := New(Options{})
	ctx := context.Background()
	nonRepo := t.TempDir()
	cases := []struct {
		name   string
		method string
		params any
		ok     bool
		kind   string
	}{
		{"status", "git.status", map[string]string{"cwd": root}, true, ""},
		{"diff", "git.diff", map[string]string{"cwd": root}, true, ""},
		{"commit", "git.commit", map[string]any{"cwd": root, "message": "m", "push": false}, true, ""},
		{"status_missing", "git.status", map[string]string{"cwd": nonRepo}, false, "not_found"},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			var frame []byte
			sess := &session{srv: srv, helloOK: true, captureWS: func(b []byte) { frame = b }}
			p, _ := json.Marshal(c.params)
			sess.dispatch(ctx, 1, c.method, p)
			if c.ok {
				var out struct {
					R json.RawMessage `json:"r"`
				}
				if json.Unmarshal(frame, &out) != nil || len(out.R) == 0 {
					t.Fatalf("%s", frame)
				}
			} else {
				var out wire.WSError
				if json.Unmarshal(frame, &out) != nil || out.E.Kind != c.kind {
					t.Fatalf("%s", frame)
				}
			}
		})
	}
}
