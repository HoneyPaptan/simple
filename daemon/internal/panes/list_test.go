package panes_test

import (
	"bufio"
	"context"
	"encoding/json"
	"fmt"
	"net"
	"path/filepath"
	"testing"

	"sh.simple/simpled/internal/agents"
	"sh.simple/simpled/internal/hc"
	"sh.simple/simpled/internal/herdr"
	"sh.simple/simpled/internal/panes"
)

func TestListHCTerminalsInWorkspace(t *testing.T) {
	dir := t.TempDir()
	store := agents.NewStore(func(string, any) {})
	store.LoadSnapshot([]hc.Terminal{{
		Address: "t2",
		Cwd:     dir,
		Labels:  map[string]string{"simple.kind": "shell"},
		State:   "live",
		Title:   "fish",
	}})
	ctx := context.Background()
	svc := panes.New(store, nil, nil)
	res, err := svc.List(ctx, dir)
	if err != nil {
		t.Fatal(err)
	}
	if len(res.Tabs) != 1 || len(res.Tabs[0].Panes) != 1 {
		t.Fatalf("tabs %+v", res.Tabs)
	}
	if res.Tabs[0].Panes[0].ID != "t2" || res.Tabs[0].Panes[0].Kind != "shell" {
		t.Fatalf("pane %+v", res.Tabs[0].Panes[0])
	}
}

func startFakeHerdrPanes(t *testing.T, workspace string) string {
	t.Helper()
	dir := t.TempDir()
	sock := filepath.Join(dir, "herdr.sock")
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
			go handleFakeHerdrPanes(c, workspace)
		}
	}()
	t.Cleanup(func() { ln.Close() })
	return sock
}

func handleFakeHerdrPanes(conn net.Conn, workspace string) {
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
		case "pane.list":
			writeHerdrResp(conn, req.ID, map[string]any{
				"type": "pane_list",
				"panes": []map[string]any{{
					"pane_id": "w1:p9", "tab_id": "w1:t2", "cwd": workspace,
					"agent": "claude", "agent_status": "working", "label": "fix",
				}, {
					"pane_id": "w1:p10", "tab_id": "w1:t2", "cwd": workspace,
					"label": "dev", "agent_status": "unknown",
				}},
			})
		case "tab.list":
			writeHerdrResp(conn, req.ID, map[string]any{
				"type": "tab_list",
				"tabs": []map[string]any{{
					"tab_id": "w1:t2", "label": "api", "pane_count": 2, "agent_status": "working",
				}},
			})
		case "agent.read", "pane.read":
			writeHerdrResp(conn, req.ID, map[string]any{
				"type": "pane_read",
				"read": map[string]any{"text": "line1\nline2", "truncated": false},
			})
		default:
			writeHerdrResp(conn, req.ID, map[string]any{"type": "ok"})
		}
	}
}

func writeHerdrResp(c net.Conn, id string, result any) {
	b, _ := json.Marshal(map[string]any{"id": id, "result": result})
	_, _ = c.Write(append(b, '\n'))
}

func TestListHerdrTabsAndShells(t *testing.T) {
	workspace := "/tmp/simple-panes-ws"
	sock := startFakeHerdrPanes(t, workspace)
	client := herdr.NewClient(sock)
	ctx := context.Background()
	store := agents.NewStore(func(string, any) {})
	svc := panes.New(store, nil, client)
	res, err := svc.List(ctx, workspace)
	if err != nil {
		t.Fatal(err)
	}
	if len(res.Tabs) != 1 || len(res.Tabs[0].Panes) != 2 {
		t.Fatalf("tabs %+v", res.Tabs)
	}
	agent := res.Tabs[0].Panes[0]
	if agent.ID != "h:w1:p9" || agent.Kind != "claude" {
		t.Fatalf("agent pane %+v", agent)
	}
	shell := res.Tabs[0].Panes[1]
	if shell.Kind != "shell" {
		t.Fatalf("shell pane %+v", shell)
	}
}

func TestPeekCaps(t *testing.T) {
	workspace := "/tmp/peek-cap"
	sock := startFakeHerdrPanes(t, workspace)
	client := herdr.NewClient(sock)
	ctx := context.Background()
	svc := panes.New(nil, nil, client)
	ids := make([]string, 0, 12)
	for i := 0; i < 12; i++ {
		ids = append(ids, fmt.Sprintf("h:w1:p%d", i))
	}
	res, err := svc.Peek(ctx, ids, 50)
	if err != nil {
		t.Fatal(err)
	}
	if len(res.Peeks) > panes.MaxPeekPanes {
		t.Fatalf("peeks %d", len(res.Peeks))
	}
	for _, peek := range res.Peeks {
		if len(peek.Text) == 0 {
			t.Fatal("expected peek text")
		}
	}
}
