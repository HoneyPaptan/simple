package agents

import (
	"bufio"
	"context"
	"encoding/json"
	"net"
	"path/filepath"
	"sync"
	"testing"
	"time"

	"sh.simple/simpled/internal/hc"
)

func TestSendPromptWhenQuiet(t *testing.T) {
	dir := t.TempDir()
	sock := filepath.Join(dir, "hc.sock")
	ln, err := net.Listen("unix", sock)
	if err != nil {
		t.Fatal(err)
	}
	var mu sync.Mutex
	reads := 0
	var sent string
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
						ID     int64  `json:"id"`
						Method string `json:"method"`
					}
					if json.Unmarshal(line, &req) != nil {
						return
					}
					switch req.Method {
					case "hello":
						writePromptLine(conn, map[string]any{"id": req.ID, "jsonrpc": "2.0", "result": map[string]any{"generation": 1}})
					case "terminal.read":
						mu.Lock()
						reads++
						n := reads
						mu.Unlock()
						text := "boot\n"
						if n >= 2 {
							text = "simple@host:~/demo$ "
						}
						writePromptLine(conn, map[string]any{
							"id": req.ID, "jsonrpc": "2.0",
							"result": map[string]any{"text": text},
						})
					case "terminal.send":
						var full struct {
							Params struct {
								Text string `json:"text"`
							} `json:"params"`
						}
						_ = json.Unmarshal(line, &full)
						mu.Lock()
						sent = full.Params.Text
						mu.Unlock()
						writePromptLine(conn, map[string]any{"id": req.ID, "jsonrpc": "2.0", "result": map[string]any{}})
					default:
						writePromptLine(conn, map[string]any{"id": req.ID, "jsonrpc": "2.0", "result": map[string]any{}})
					}
				}
			}(c)
		}
	}()
	t.Cleanup(func() { ln.Close() })
	client := hc.NewClient(sock)
	ctx := context.Background()
	if err := client.Call(ctx, "hello", map[string]any{"generation": 1}, nil); err != nil {
		t.Fatal(err)
	}
	SendPromptWhenQuiet(ctx, client, "t1", "shell", "hello there", nil)
	deadline := time.After(5 * time.Second)
	for {
		mu.Lock()
		got := sent
		mu.Unlock()
		if got == "hello there" {
			break
		}
		select {
		case <-deadline:
			t.Fatalf("sent %q reads %d", got, reads)
		case <-time.After(50 * time.Millisecond):
		}
	}
	mu.Lock()
	sent = ""
	mu.Unlock()
	SendPromptWhenQuiet(ctx, client, "t2", "fx", "fx prompt", nil)
	deadline = time.After(5 * time.Second)
	for {
		mu.Lock()
		got := sent
		mu.Unlock()
		if got == "fx prompt" {
			return
		}
		select {
		case <-deadline:
			t.Fatalf("fx sent %q", got)
		case <-time.After(50 * time.Millisecond):
		}
	}
}

func writePromptLine(c net.Conn, v any) {
	b, _ := json.Marshal(v)
	b = append(b, '\n')
	_, _ = c.Write(b)
}

func TestPendingClaudeInput(t *testing.T) {
	stuck := "Claude Code\n───\n❯ [Image #1]What screen is this?\n───\n  ⏸ manual mode on\n"
	if got, ok := pendingClaudeInput(stuck); !ok || got != "[Image #1]What screen is this?" {
		t.Fatalf("stuck input not found: %q %v", got, ok)
	}
	if _, ok := pendingClaudeInput("● done\n❯ \n  ⏸ manual mode on\n"); ok {
		t.Fatal("empty input box reported as pending")
	}
	if _, ok := pendingClaudeInput("no prompt box here"); ok {
		t.Fatal("screen without input box reported as pending")
	}
}

func TestMapStatusKilledIsNotAnError(t *testing.T) {
	code := int32(1)
	term := int32(143)
	cases := []struct {
		term hc.Terminal
		want string
	}{
		{hc.Terminal{State: "finished", Reason: "killed"}, "done"},
		{hc.Terminal{State: "finished", ExitCode: &code}, "failed"},
		{hc.Terminal{State: "finished", Reason: "exited", ExitCode: &term}, "done"},
	}
	for _, c := range cases {
		if got, _ := MapStatus(c.term); got != c.want {
			t.Fatalf("%+v: got %s want %s", c.term, got, c.want)
		}
	}
}
