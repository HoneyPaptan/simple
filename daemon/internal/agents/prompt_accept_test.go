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

func fakeAcceptHC(t *testing.T, workingAfterEnters int) (*hc.Client, func() int) {
	t.Helper()
	sock := filepath.Join(t.TempDir(), "hc.sock")
	ln, err := net.Listen("unix", sock)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { ln.Close() })
	var mu sync.Mutex
	enters := 0
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
					var result any = map[string]any{}
					switch req.Method {
					case "hello":
						result = map[string]any{"generation": 1}
					case "terminal.keys":
						mu.Lock()
						enters++
						mu.Unlock()
					case "terminal.list":
						mu.Lock()
						state := "idle"
						if enters >= workingAfterEnters {
							state = "working"
						}
						mu.Unlock()
						result = map[string]any{"terminals": []map[string]any{{
							"address": "t1", "state": "live", "status": map[string]any{"state": state},
						}}}
					}
					writePromptLine(conn, map[string]any{"id": req.ID, "jsonrpc": "2.0", "result": result})
				}
			}(c)
		}
	}()
	return hc.NewClient(sock), func() int { mu.Lock(); defer mu.Unlock(); return enters }
}

func TestSubmitUntilAcceptedPressesEnterUntilWorking(t *testing.T) {
	client, enters := fakeAcceptHC(t, 2)
	submitUntilAccepted(context.Background(), client, "t1", 0, 5*time.Millisecond, 5)
	if got := enters(); got != 2 {
		t.Fatalf("enters = %d, want 2", got)
	}
}

func TestSubmitUntilAcceptedLeavesAnAcceptedPromptAlone(t *testing.T) {
	client, enters := fakeAcceptHC(t, 0)
	submitUntilAccepted(context.Background(), client, "t1", 0, 5*time.Millisecond, 3)
	if got := enters(); got != 0 {
		t.Fatalf("enters = %d, want 0", got)
	}
}
