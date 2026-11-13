package api

import (
	"context"
	"encoding/json"
	"os"
	"path/filepath"
	"testing"

	"sh.simple/simpled/internal/wire"
)

func TestDispatchFilesMethods(t *testing.T) {
	home := t.TempDir()
	t.Setenv("HOME", home)
	root := filepath.Join(home, "ws")
	if err := os.MkdirAll(root, 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(root, "f.txt"), []byte("alpha\nbeta"), 0o644); err != nil {
		t.Fatal(err)
	}
	srv := New(Options{})
	ctx := context.Background()

	cases := []struct {
		name   string
		method string
		params any
		ok     bool
		kind   string
		check  func(t *testing.T, raw json.RawMessage)
	}{
		{
			name:   "list",
			method: "files.list",
			params: map[string]string{"path": root},
			ok:     true,
			check: func(t *testing.T, raw json.RawMessage) {
				var r wire.FilesListResult
				if json.Unmarshal(raw, &r) != nil || len(r.Entries) == 0 {
					t.Fatalf("%s", raw)
				}
			},
		},
		{
			name:   "read",
			method: "files.read",
			params: map[string]string{"path": filepath.Join(root, "f.txt")},
			ok:     true,
			check: func(t *testing.T, raw json.RawMessage) {
				var r wire.FilesReadResult
				if json.Unmarshal(raw, &r) != nil || r.Lines != 2 {
					t.Fatalf("%+v", r)
				}
			},
		},
		{
			name:   "write",
			method: "files.write",
			params: map[string]string{"path": filepath.Join(root, "out.txt"), "text": "ok"},
			ok:     true,
		},
		{
			name:   "mkdir",
			method: "files.mkdir",
			params: map[string]string{"path": root, "name": "sub"},
			ok:     true,
		},
		{
			name:   "read_missing",
			method: "files.read",
			params: map[string]string{"path": filepath.Join(root, "nope.txt")},
			ok:     false,
			kind:   "not_found",
		},
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
					t.Fatalf("frame %s", frame)
				}
				if c.check != nil {
					c.check(t, out.R)
				}
			} else {
				var out wire.WSError
				if json.Unmarshal(frame, &out) != nil || out.E.Kind != c.kind {
					t.Fatalf("frame %s", frame)
				}
			}
		})
	}
}
