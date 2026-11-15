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
	"sh.simple/simpled/internal/wire"
)

func TestFailedSendFreesCidSoRetryReachesTheTerminal(t *testing.T) {
	var mu sync.Mutex
	var sends []string
	hcClient := startFakeHCSends(t, &mu, &sends)
	agentStore := agents.NewStore(func(string, any) {})
	agentStore.Upsert(wire.Agent{ID: "t1", Kind: "shell", Status: "idle"})
	srv := New(Options{HC: hcClient, Agents: agentStore, Hub: hub.New("e_retry")})
	t.Cleanup(func() {
		srv.sendWatchMu.Lock()
		if cancel, ok := srv.sendWatch["t1"]; ok {
			cancel()
		}
		srv.sendWatchMu.Unlock()
	})
	ctx := context.Background()
	sess := &session{srv: srv, helloOK: true, deviceID: "phone"}
	var frame []byte
	sess.captureWS = func(b []byte) { frame = b }
	p := []byte(`{"id":"t1","text":"hello","cid":"c1"}`)

	sess.dispatch(ctx, 1, "agent.send", p)
	if got := sendCount(&mu, &sends); got != 1 {
		t.Fatalf("first send %d", got)
	}
	cancelSendWatch(srv, "t1")

	sess.dispatch(ctx, 2, "agent.send", p)
	if got := sendCount(&mu, &sends); got != 1 {
		t.Fatalf("claimed cid was sent again: %d", got)
	}

	srv.latchFailedSend("t1", "Not logged in")
	q := srv.promptQueue()
	q.mu.Lock()
	q.lastSent["t1"] = time.Now().Add(-time.Minute)
	q.mu.Unlock()

	sess.dispatch(ctx, 3, "agent.send", p)
	if got := sendCount(&mu, &sends); got != 2 {
		t.Fatalf("retry after failure %d frame %s", got, frame)
	}
}

func cancelSendWatch(srv *Server, id string) {
	srv.sendWatchMu.Lock()
	defer srv.sendWatchMu.Unlock()
	if cancel, ok := srv.sendWatch[id]; ok {
		cancel()
		delete(srv.sendWatch, id)
	}
}

func sendCount(mu *sync.Mutex, sends *[]string) int {
	mu.Lock()
	defer mu.Unlock()
	return len(*sends)
}

func startFakeHCSends(t *testing.T, mu *sync.Mutex, sends *[]string) *hc.Client {
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
					if req.Method == "terminal.send" {
						var params struct {
							Text string `json:"text"`
						}
						_ = json.Unmarshal(req.Params, &params)
						mu.Lock()
						*sends = append(*sends, params.Text)
						mu.Unlock()
					}
					writeTestLine(conn, map[string]any{"id": req.ID, "jsonrpc": "2.0", "result": map[string]any{}})
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
