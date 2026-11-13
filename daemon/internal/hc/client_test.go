package hc_test

import (
	"bufio"
	"context"
	"encoding/json"
	"net"
	"os"
	"path/filepath"
	"sync"
	"testing"
	"time"

	"sh.simple/simpled/internal/hc"
)

func startFakeHC(t *testing.T, onSubscribe func(conn net.Conn)) string {
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
						resp := map[string]any{
							"id": req.ID, "jsonrpc": "2.0",
							"result": map[string]any{"generation": 1, "version": "0.1.17"},
						}
						writeLine(conn, resp)
					case "events.subscribe":
						sub := map[string]any{
							"id": req.ID, "jsonrpc": "2.0",
							"result": map[string]any{
								"seq": 1, "channel": nil,
								"terminals": []any{},
							},
						}
						writeLine(conn, sub)
						if onSubscribe != nil {
							onSubscribe(conn)
						}
						ioWait(conn)
					case "fail":
						writeLine(conn, map[string]any{"id": req.ID, "jsonrpc": "2.0", "error": map[string]any{"code": -32000, "message": "boom"}})
					default:
						resp := map[string]any{"id": req.ID, "jsonrpc": "2.0", "result": map[string]any{"echo": req.Params}}
						writeLine(conn, resp)
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

func ioWait(c net.Conn) {
	buf := make([]byte, 1)
	for {
		c.SetReadDeadline(time.Now().Add(100 * time.Millisecond))
		if _, err := c.Read(buf); err != nil {
			return
		}
	}
}

func TestClientHelloAndSubscribe(t *testing.T) {
	var mu sync.Mutex
	var eventConn net.Conn
	sock := startFakeHC(t, func(c net.Conn) {
		mu.Lock()
		eventConn = c
		mu.Unlock()
	})
	client := hc.NewClient(sock)
	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()
	sub, ch, err := client.Subscribe(ctx)
	if err != nil {
		t.Fatal(err)
	}
	if sub.Seq != 1 {
		t.Fatalf("seq %d", sub.Seq)
	}
	mu.Lock()
	c := eventConn
	mu.Unlock()
	if c != nil {
		note := map[string]any{
			"jsonrpc": "2.0", "method": "event",
			"params": map[string]any{
				"seq": 2, "terminal": "t1", "type": "created",
				"data": map[string]any{
					"address": "t1", "argv": []string{"claude"}, "cwd": "/tmp",
					"created_ms": 1000, "state": "live", "labels": map[string]string{"agent": "claude"},
				},
			},
		}
		writeLine(c, note)
	}
	select {
	case ev := <-ch:
		if ev.Terminal != "t1" || ev.Type != "created" {
			t.Fatalf("%+v", ev)
		}
	case <-time.After(2 * time.Second):
		t.Fatal("timeout")
	}
	cancel()
}

func TestClientCall(t *testing.T) {
	sock := startFakeHC(t, nil)
	client := hc.NewClient(sock)
	ctx := context.Background()
	var out json.RawMessage
	if err := client.Call(ctx, "terminal.list", map[string]any{}, &out); err != nil {
		t.Fatal(err)
	}
	_ = os.Getenv
}

func startFakeHCExecDelay(t *testing.T, delay time.Duration) string {
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
						ID     int64  `json:"id"`
						Method string `json:"method"`
					}
					if json.Unmarshal(line, &req) != nil {
						return
					}
					switch req.Method {
					case "hello":
						writeLine(conn, map[string]any{"id": req.ID, "jsonrpc": "2.0", "result": map[string]any{"generation": 1}})
					case "machine.exec":
						time.Sleep(delay)
						writeLine(conn, map[string]any{"id": req.ID, "jsonrpc": "2.0", "result": map[string]any{"stdout": ""}})
					default:
						writeLine(conn, map[string]any{"id": req.ID, "jsonrpc": "2.0", "result": map[string]any{}})
					}
				}
			}(c)
		}
	}()
	t.Cleanup(func() { ln.Close() })
	return sock
}

func TestOneShotCallDoesNotWaitOnSharedCallLock(t *testing.T) {
	sock := startFakeHCExecDelay(t, 800*time.Millisecond)
	client := hc.NewClient(sock)
	ctx := context.Background()
	done := make(chan struct{})
	go func() {
		var out json.RawMessage
		_ = client.Call(ctx, "machine.exec", map[string]any{"line": "true"}, &out)
		close(done)
	}()
	time.Sleep(50 * time.Millisecond)
	start := time.Now()
	var out json.RawMessage
	if err := client.OneShotCall(ctx, "terminal.list", map[string]any{}, &out); err != nil {
		t.Fatal(err)
	}
	if time.Since(start) > 400*time.Millisecond {
		t.Fatal("one shot call blocked on shared client lock")
	}
	<-done
}

func TestClientConcurrentCallsGetTheirOwnResponses(t *testing.T) {
	sock := startFakeHC(t, nil)
	client := hc.NewClient(sock)
	ctx := context.Background()
	var wg sync.WaitGroup
	errs := make(chan string, 400)
	for _, term := range []string{"t1", "t2", "t3", "t4"} {
		wg.Add(1)
		go func(term string) {
			defer wg.Done()
			for i := 0; i < 100; i++ {
				var out struct {
					Echo struct {
						Terminal string `json:"terminal"`
					} `json:"echo"`
				}
				if err := client.Call(ctx, "terminal.read", map[string]any{"terminal": term}, &out); err != nil {
					errs <- err.Error()
					return
				}
				if out.Echo.Terminal != term {
					errs <- "asked " + term + " got " + out.Echo.Terminal
					return
				}
			}
		}(term)
	}
	wg.Wait()
	close(errs)
	for e := range errs {
		t.Fatal(e)
	}
}

func TestFailedCallKeepsSubscribedStatusUp(t *testing.T) {
	sock := startFakeHC(t, nil)
	client := hc.NewClient(sock)
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	go client.MaintainSubscribe(ctx, nil, nil)
	deadline := time.Now().Add(2 * time.Second)
	for client.Status() != hc.StatusUp {
		if time.Now().After(deadline) {
			t.Fatal("subscription never came up")
		}
		time.Sleep(10 * time.Millisecond)
	}
	var out json.RawMessage
	if err := client.Call(ctx, "fail", map[string]any{}, &out); err == nil {
		t.Fatal("expected call error")
	}
	if client.Status() != hc.StatusUp {
		t.Fatal("a failed call must not mark the host control link down")
	}
	if err := client.Call(ctx, "terminal.list", map[string]any{}, &out); err != nil {
		t.Fatal(err)
	}
}

func TestCallReturnsWhenServerNeverAnswers(t *testing.T) {
	sock := startFakeHCExecDelay(t, time.Hour)
	client := hc.NewClient(sock)
	ctx, cancel := context.WithTimeout(context.Background(), 200*time.Millisecond)
	defer cancel()
	done := make(chan error, 1)
	go func() { done <- client.Call(ctx, "machine.exec", map[string]any{}, nil) }()
	select {
	case err := <-done:
		if err == nil {
			t.Fatal("expected a timeout error")
		}
	case <-time.After(2 * time.Second):
		t.Fatal("Call blocked past its context deadline")
	}
}
