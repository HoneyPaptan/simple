package api_test

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
	"sh.simple/simpled/internal/api"
	"sh.simple/simpled/internal/config"
	"sh.simple/simpled/internal/hc"
	"sh.simple/simpled/internal/inbox"
)

func TestBackgroundPromptDetectWithoutWatch(t *testing.T) {
	home := t.TempDir()
	store := config.NewStore(home, t.TempDir())
	_ = store.EnsureDirs()
	screenText := "Overwrite config.json? [y/N] "
	sock := startFakeHCReads(t, screenText)
	hcClient := hc.NewClient(sock)
	agentStore := agents.NewStore(func(string, any) {})
	inboxStore := inbox.New(store, func(string, any) {})
	prompts := api.NewPromptTracker(agentStore, inboxStore)
	screens := api.NewScreenWatcher(hcClient, nil, func(string, any) {}, prompts, agentStore)
	agentStore.SetPollSync(screens.SyncPollers)

	agentStore.LoadSnapshot([]hc.Terminal{{
		Address: "t1",
		Argv:    []string{"bash"},
		Cwd:     home,
		State:   "live",
	}})
	if _, ok := agentStore.Get("t1"); !ok {
		t.Fatal("agent missing")
	}

	ctx := context.Background()
	deadline := time.Now().Add(2 * time.Second)
	for time.Now().Before(deadline) {
		screens.PollAgentOnce(ctx, "t1")
		screens.PollAgentOnce(ctx, "t1")
		time.Sleep(650 * time.Millisecond)
		items, _ := inboxStore.List()
		if len(items) == 1 && items[0].Kind == "prompt" {
			return
		}
	}
	items, _ := inboxStore.List()
	t.Fatalf("expected one prompt item, got %d", len(items))
}

func startFakeHCReads(t *testing.T, screen string) string {
	t.Helper()
	dir := t.TempDir()
	sock := filepath.Join(dir, "hc.sock")
	ln, err := net.Listen("unix", sock)
	if err != nil {
		t.Fatal(err)
	}
	var mu sync.Mutex
	text := screen
	go func() {
		for {
			c, err := ln.Accept()
			if err != nil {
				return
			}
			go func(c net.Conn) {
				defer c.Close()
				br := bufio.NewReader(c)
				for {
					line, err := br.ReadBytes('\n')
					if err != nil {
						return
					}
					var req struct {
						ID     int64  `json:"id"`
						Method string `json:"method"`
					}
					if json.Unmarshal(line, &req) != nil {
						return
					}
					switch req.Method {
					case "hello":
						writeLine(c, map[string]any{"id": req.ID, "jsonrpc": "2.0", "result": map[string]any{"generation": 1}})
					case "events.subscribe":
						writeLine(c, map[string]any{
							"id": req.ID, "jsonrpc": "2.0",
							"result": map[string]any{"seq": 1, "terminals": []any{}},
						})
					case "terminal.read":
						mu.Lock()
						cur := text
						mu.Unlock()
						writeLine(c, map[string]any{
							"id": req.ID, "jsonrpc": "2.0",
							"result": map[string]any{"text": cur},
						})
					default:
						writeLine(c, map[string]any{"id": req.ID, "jsonrpc": "2.0", "result": map[string]any{}})
					}
				}
			}(c)
		}
	}()
	t.Cleanup(func() { ln.Close() })
	return sock
}

func writeLine(c net.Conn, v any) {
	b, _ := json.Marshal(v)
	b = append(b, '\n')
	_, _ = c.Write(b)
}
