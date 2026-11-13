package api

import (
	"bufio"
	"context"
	"encoding/json"
	"fmt"
	"net"
	"path/filepath"
	"sync"
	"testing"
	"time"

	"sh.simple/simpled/internal/agents"
	"sh.simple/simpled/internal/hc"
	"sh.simple/simpled/internal/hub"
)

type runCapture struct {
	mu      sync.Mutex
	runs    []map[string]any
	execs   []string
	nextID  int
	updates int
}

func startFakeHCForStart(t *testing.T, cap *runCapture) *hc.Client {
	t.Helper()
	dir := t.TempDir()
	sock := filepath.Join(dir, "hc.sock")
	ln, err := net.Listen("unix", sock)
	if err != nil {
		t.Fatal(err)
	}
	go func() {
		for {
			c, err := ln.Accept()
			if err != nil {
				return
			}
			go func(conn net.Conn) {
				defer conn.Close()
				br := bufio.NewReader(conn)
				for {
					line, err := br.ReadBytes('\n')
					if err != nil {
						return
					}
					var req struct {
						ID     int64           `json:"id"`
						Method string          `json:"method"`
						Params json.RawMessage `json:"params"`
					}
					if json.Unmarshal(line, &req) != nil {
						return
					}
					switch req.Method {
					case "hello":
						writeTestLine(conn, map[string]any{"id": req.ID, "jsonrpc": "2.0", "result": map[string]any{"generation": 1}})
					case "events.subscribe":
						writeTestLine(conn, map[string]any{
							"id": req.ID, "jsonrpc": "2.0",
							"result": map[string]any{"seq": 1, "terminals": []any{}},
						})
					case "terminal.run":
						var params map[string]any
						_ = json.Unmarshal(req.Params, &params)
						cap.mu.Lock()
						cap.runs = append(cap.runs, params)
						cap.nextID++
						addr := fmt.Sprintf("t%d", cap.nextID)
						cap.mu.Unlock()
						writeTestLine(conn, map[string]any{
							"id": req.ID, "jsonrpc": "2.0",
							"result": map[string]any{
								"address": addr, "argv": params["argv"], "cwd": params["cwd"],
								"created_ms": 1, "state": "live", "labels": params["labels"],
							},
						})
					case "machine.exec":
						var params struct {
							Line string `json:"line"`
						}
						_ = json.Unmarshal(req.Params, &params)
						cap.mu.Lock()
						cap.execs = append(cap.execs, params.Line)
						cap.mu.Unlock()
						writeTestLine(conn, map[string]any{
							"id": req.ID, "jsonrpc": "2.0",
							"result": map[string]any{"stdout": ""},
						})
					default:
						writeTestLine(conn, map[string]any{"id": req.ID, "jsonrpc": "2.0", "result": map[string]any{}})
					}
				}
			}(c)
		}
	}()
	t.Cleanup(func() { ln.Close() })
	client := hc.NewClient(sock)
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()
	if _, _, err := client.Subscribe(ctx); err != nil {
		t.Fatal(err)
	}
	var boot json.RawMessage
	if err := client.Call(context.Background(), "terminal.list", map[string]any{}, &boot); err != nil {
		t.Fatal(err)
	}
	time.Sleep(20 * time.Millisecond)
	return client
}

func writeTestLine(c net.Conn, v any) {
	b, _ := json.Marshal(v)
	b = append(b, '\n')
	_, _ = c.Write(b)
}

func TestDispatchAgentStartMethods(t *testing.T) {
	cap := &runCapture{}
	hcClient := startFakeHCForStart(t, cap)
	var updates int
	agentStore := agents.NewStore(func(ev string, _ any) {
		if ev == "agent.update" {
			updates++
		}
	})
	srv := New(Options{HC: hcClient, Agents: agentStore, Hub: hub.New("e_test"), SimpledPath: "/tmp/simpled"})
	ctx := context.Background()

	cases := []struct {
		name   string
		params map[string]any
		ok     bool
		kind   string
		check  func(t *testing.T)
	}{
		{
			name: "shell",
			params: map[string]any{
				"kind": "shell", "cwd": "/tmp/demo", "mode": "ask",
			},
			ok: true,
			check: func(t *testing.T) {
				cap.mu.Lock()
				defer cap.mu.Unlock()
				if len(cap.runs) != 1 {
					t.Fatalf("runs %d", len(cap.runs))
				}
				argv, _ := cap.runs[0]["argv"].([]any)
				if len(argv) < 2 || argv[len(argv)-1] != "-l" {
					t.Fatalf("argv %v", argv)
				}
				labels, _ := cap.runs[0]["labels"].(map[string]any)
				if labels["simple.kind"] != "shell" {
					t.Fatalf("labels %v", labels)
				}
			},
		},
		{
			name: "claude_argv",
			params: map[string]any{
				"kind": "claude", "cwd": "/tmp/demo", "mode": "ask",
			},
			ok: true,
			check: func(t *testing.T) {
				cap.mu.Lock()
				defer cap.mu.Unlock()
				last := cap.runs[len(cap.runs)-1]
				argv, _ := last["argv"].([]any)
				env, _ := last["env"].(map[string]any)
				if len(argv) == 0 || argv[0] != "claude" || env["CLAUDE_CODE_CHILD_SESSION"] != "" {
					t.Fatalf("argv %v", argv)
				}
				found := false
				for i := 0; i+1 < len(argv); i++ {
					if argv[i] == "--permission-mode" && argv[i+1] == "default" {
						found = true
					}
				}
				if !found {
					t.Fatalf("argv %v", argv)
				}
			},
		},
		{
			name: "worktree",
			params: map[string]any{
				"kind": "shell", "cwd": "/tmp/repo", "mode": "ask",
				"worktree": map[string]any{"branch": "feat"},
			},
			ok: true,
			check: func(t *testing.T) {
				cap.mu.Lock()
				defer cap.mu.Unlock()
				if len(cap.execs) == 0 {
					t.Fatal("no worktree exec")
				}
				if cap.execs[0] == "" {
					t.Fatal("empty exec")
				}
				last := cap.runs[len(cap.runs)-1]
				if last["cwd"] != "/tmp/repo/.worktrees/feat" {
					t.Fatalf("cwd %v", last["cwd"])
				}
			},
		},
		{
			name:   "unsupported",
			params: map[string]any{"kind": "unknown", "cwd": "/tmp", "mode": "ask"},
			ok:     false,
			kind:   "agent_unsupported",
		},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			before := updates
			var frame []byte
			sess := &session{srv: srv, helloOK: true, captureWS: func(b []byte) { frame = b }}
			p, _ := json.Marshal(c.params)
			sess.dispatch(ctx, 42, "agent.start", p)
			if c.ok {
				var out struct {
					R json.RawMessage `json:"r"`
				}
				if json.Unmarshal(frame, &out) != nil {
					t.Fatalf("%s", frame)
				}
				if updates <= before {
					t.Fatal("expected agent.update")
				}
				if c.check != nil {
					c.check(t)
				}
			} else {
				var out struct {
					E struct {
						Kind string `json:"kind"`
					} `json:"e"`
				}
				if json.Unmarshal(frame, &out) != nil || out.E.Kind != c.kind {
					t.Fatalf("%s", frame)
				}
			}
		})
	}
}
