package herdr_test

import (
	"bufio"
	"context"
	"encoding/json"
	"net"
	"path/filepath"
	"testing"
	"time"

	"sh.simple/simpled/internal/herdr"
)

func startFakeHerdr(t *testing.T) string {
	t.Helper()
	dir := t.TempDir()
	sock := filepath.Join(dir, "herdr.sock")
	ln, err := net.Listen("unix", sock)
	if err != nil {
		t.Fatal(err)
	}
	agents := []herdr.AgentInfo{
		{
			Agent: "claude", AgentStatus: "working", PaneID: "w1:p1",
			Cwd: "/tmp", Revision: 1, StateChangeSeq: 1,
		},
	}
	go func() {
		for {
			c, err := ln.Accept()
			if err != nil {
				return
			}
			go handleFakeHerdr(c, agents)
		}
	}()
	t.Cleanup(func() { ln.Close() })
	return sock
}

func handleFakeHerdr(conn net.Conn, agents []herdr.AgentInfo) {
	defer conn.Close()
	br := bufio.NewReader(conn)
	for {
		line, err := br.ReadBytes('\n')
		if err != nil {
			return
		}
		var req struct {
			ID     string          `json:"id"`
			Method string          `json:"method"`
			Params json.RawMessage `json:"params"`
		}
		if json.Unmarshal(line, &req) != nil {
			return
		}
		switch req.Method {
		case "ping":
			writeResp(conn, req.ID, map[string]any{"type": "pong", "version": "0.9.1", "protocol": 22})
		case "agent.list":
			writeResp(conn, req.ID, map[string]any{"type": "agent_list", "agents": agents})
		case "agent.read":
			writeResp(conn, req.ID, map[string]any{
				"type": "pane_read",
				"read": map[string]any{"text": "hello", "truncated": false},
			})
		case "agent.prompt", "agent.send_keys":
			writeResp(conn, req.ID, map[string]any{"type": "ok"})
		case "events.subscribe":
			writeResp(conn, req.ID, map[string]any{"type": "subscription_started"})
			waitRead(conn)
		default:
			writeResp(conn, req.ID, map[string]any{"type": "echo", "method": req.Method})
		}
	}
}

func writeResp(c net.Conn, id string, result any) {
	b, _ := json.Marshal(map[string]any{"id": id, "result": result})
	b = append(b, '\n')
	_, _ = c.Write(b)
}

func waitRead(c net.Conn) {
	buf := make([]byte, 1)
	for {
		c.SetReadDeadline(time.Now().Add(50 * time.Millisecond))
		if _, err := c.Read(buf); err != nil {
			return
		}
	}
}

func TestClientListReadPrompt(t *testing.T) {
	sock := startFakeHerdr(t)
	c := herdr.NewClient(sock)
	ctx := context.Background()
	list, err := c.AgentList(ctx)
	if err != nil || len(list) != 1 || list[0].PaneID != "w1:p1" {
		t.Fatalf("list: %v err %v", list, err)
	}
	text, err := c.AgentRead(ctx, "w1:p1", "recent_unwrapped", nil)
	if err != nil || text != "hello" {
		t.Fatalf("read %q err %v", text, err)
	}
	if err := c.AgentPrompt(ctx, "w1:p1", "hi"); err != nil {
		t.Fatal(err)
	}
	if err := c.Interrupt(ctx, "w1:p1"); err != nil {
		t.Fatal(err)
	}
}

func TestFeedNotifiesOnRevision(t *testing.T) {
	sock := startFakeHerdr(t)
	c := herdr.NewClient(sock)
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	ch := make(chan []herdr.AgentInfo, 2)
	feed := herdr.NewFeed(c, func(list []herdr.AgentInfo) {
		select {
		case ch <- list:
		default:
		}
	})
	go feed.Run(ctx)
	select {
	case <-ch:
	case <-time.After(3 * time.Second):
		t.Fatal("timeout waiting for feed")
	}
}
