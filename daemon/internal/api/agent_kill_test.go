package api

import (
	"bufio"
	"context"
	"encoding/json"
	"net"
	"path/filepath"
	"sync"
	"testing"
	"time"

	"sh.simple/simpled/internal/agents"
	"sh.simple/simpled/internal/hc"
	"sh.simple/simpled/internal/hub"
)

func TestDispatchAgentKillCallsHC(t *testing.T) {
	var mu sync.Mutex
	var kills []map[string]any
	hcClient := startFakeHCKill(t, &mu, &kills)
	agentStore := agents.NewStore(func(string, any) {})
	srv := New(Options{HC: hcClient, Agents: agentStore, Hub: hub.New("e_kill")})
	ctx := context.Background()
	sess := &session{srv: srv, helloOK: true}
	var frame []byte
	sess.captureWS = func(b []byte) { frame = b }

	p, _ := json.Marshal(map[string]any{"id": "t9"})
	sess.dispatch(ctx, 3, "agent.kill", p)

	var res struct {
		E *struct {
			Kind string `json:"kind"`
		} `json:"e"`
	}
	if json.Unmarshal(frame, &res) != nil || res.E != nil {
		t.Fatalf("response %s", frame)
	}
	mu.Lock()
	defer mu.Unlock()
	if len(kills) != 1 {
		t.Fatalf("kills %d", len(kills))
	}
	if kills[0]["terminal"] != "t9" {
		t.Fatalf("params %v", kills[0])
	}
}

func startFakeHCKill(t *testing.T, mu *sync.Mutex, kills *[]map[string]any) *hc.Client {
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
					case "terminal.list":
						writeTestLine(conn, map[string]any{
							"id": req.ID, "jsonrpc": "2.0",
							"result": map[string]any{"terminals": []any{}},
						})
					case "terminal.kill":
						var params map[string]any
						_ = json.Unmarshal(req.Params, &params)
						mu.Lock()
						*kills = append(*kills, params)
						mu.Unlock()
						writeTestLine(conn, map[string]any{"id": req.ID, "jsonrpc": "2.0", "result": map[string]any{}})
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
