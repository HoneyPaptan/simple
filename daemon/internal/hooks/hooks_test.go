package hooks_test

import (
	"context"
	"encoding/json"
	"testing"
	"time"

	"sh.simple/simpled/internal/agents"
	"sh.simple/simpled/internal/hc"
	"sh.simple/simpled/internal/hooks"
	"sh.simple/simpled/internal/inbox"
	"sh.simple/simpled/internal/config"
	"sh.simple/simpled/internal/transcript"
	"sh.simple/simpled/internal/wire"
)

func testHookEnv(t *testing.T) (*hooks.Daemon, *inbox.Store, *agents.Store) {
	t.Helper()
	dir := t.TempDir()
	cfg := &config.Store{StateDir: dir}
	inboxStore := inbox.New(cfg, func(string, any) {})
	agentStore := agents.NewStore(func(string, any) {})
	transcripts := transcript.NewManager(func(string, any) {})
	term := hc.Terminal{
		Address:   "t1",
		Argv:      []string{"claude"},
		Cwd:       "/tmp",
		CreatedMs: 1000,
		Labels:    map[string]string{"simple.mode": "ask"},
		State:     "live",
	}
	agentStore.LoadSnapshot([]hc.Terminal{term})
	d := hooks.New(inboxStore, agentStore, transcripts, "simpled")
	return d, inboxStore, agentStore
}

func TestSessionStartBindsTranscript(t *testing.T) {
	d, _, _ := testHookEnv(t)
	body, _ := json.Marshal(map[string]string{
		"session_id":      "sess1",
		"transcript_path": "/tmp/t.jsonl",
		"cwd":             "/tmp",
	})
	resp, ok := d.Handle(context.Background(), "claude", "t1", "SessionStart", body)
	if !ok || len(resp) == 0 {
		t.Fatal("session start")
	}
}

func TestPermissionFullModeAllows(t *testing.T) {
	dir := t.TempDir()
	cfg := &config.Store{StateDir: dir}
	inboxStore := inbox.New(cfg, func(string, any) {})
	agentStore := agents.NewStore(func(string, any) {})
	mode := "full"
	term := hc.Terminal{
		Address:   "t1",
		Argv:      []string{"claude"},
		Cwd:       "/tmp",
		CreatedMs: 1000,
		Labels:    map[string]string{"simple.mode": mode},
		State:     "live",
	}
	agentStore.LoadSnapshot([]hc.Terminal{term})
	d := hooks.New(inboxStore, agentStore, transcript.NewManager(func(string, any) {}), "simpled")
	toolInput, _ := json.Marshal(map[string]string{"command": "npm test"})
	body, _ := json.Marshal(map[string]any{"tool_name": "Bash", "tool_input": json.RawMessage(toolInput)})
	resp, ok := d.Handle(context.Background(), "claude", "t1", "PermissionRequest", body)
	if !ok {
		t.Fatal("not ok")
	}
	if string(resp) == "" {
		t.Fatal("empty")
	}
	list, _ := inboxStore.List()
	if len(list) != 0 {
		t.Fatalf("inbox %d", len(list))
	}
}

func TestPermissionWaitsForAnswer(t *testing.T) {
	d, inboxStore, _ := testHookEnv(t)
	toolInput, _ := json.Marshal(map[string]string{"line": "npm test"})
	body, _ := json.Marshal(map[string]any{"tool_name": "Bash", "tool_input": json.RawMessage(toolInput)})
	done := make(chan json.RawMessage, 1)
	go func() {
		resp, ok := d.Handle(context.Background(), "claude", "t1", "PermissionRequest", body)
		if ok {
			done <- resp
		} else {
			done <- nil
		}
	}()
	var id string
	for i := 0; i < 200; i++ {
		list, _ := inboxStore.List()
		if len(list) > 0 {
			id = list[0].ID
			break
		}
		time.Sleep(5 * time.Millisecond)
	}
	if id == "" {
		t.Fatal("no inbox item")
	}
	d.DeliverAnswer(id, "deny", nil, nil)
	select {
	case resp := <-done:
		if resp == nil || string(resp) == "" {
			t.Fatal("no response")
		}
	case <-time.After(2 * time.Second):
		t.Fatal("timeout")
	}
}

func TestOpenCodePermissionHook(t *testing.T) {
	d, inboxStore, _ := testHookEnv(t)
	body, _ := json.Marshal(map[string]any{
		"id":        "perm_1",
		"type":      "bash",
		"sessionID": "ses_1",
		"title":     "Run npm test",
		"metadata":  map[string]any{"message": "npm test"},
	})
	done := make(chan json.RawMessage, 1)
	go func() {
		resp, ok := d.Handle(context.Background(), "opencode", "t1", "permission.ask", body)
		if ok {
			done <- resp
		} else {
			done <- nil
		}
	}()
	var id string
	for i := 0; i < 200; i++ {
		list, _ := inboxStore.List()
		if len(list) > 0 {
			id = list[0].ID
			break
		}
		time.Sleep(5 * time.Millisecond)
	}
	if id == "" {
		t.Fatal("no inbox item")
	}
	d.DeliverAnswer(id, "deny", nil, nil)
	select {
	case resp := <-done:
		if resp == nil || string(resp) != `{"status":"deny"}` {
			t.Fatalf("resp %s", resp)
		}
	case <-time.After(2 * time.Second):
		t.Fatal("timeout")
	}
}

func TestOpenCodePermissionReply(t *testing.T) {
	d, inboxStore, _ := testHookEnv(t)
	body, _ := json.Marshal(map[string]any{
		"type": "permission.asked",
		"properties": map[string]any{
			"id":         "perm_1",
			"sessionID":  "ses_1",
			"permission": "bash",
			"metadata":   map[string]any{"message": "npm test"},
		},
	})
	done := make(chan json.RawMessage, 1)
	go func() {
		resp, ok := d.Handle(context.Background(), "opencode", "t1", "permission.asked", body)
		if ok {
			done <- resp
		} else {
			done <- nil
		}
	}()
	var id string
	for i := 0; i < 200; i++ {
		list, _ := inboxStore.List()
		if len(list) > 0 {
			id = list[0].ID
			break
		}
		time.Sleep(5 * time.Millisecond)
	}
	if id == "" {
		t.Fatal("no inbox item")
	}
	d.DeliverAnswer(id, "allow", nil, nil)
	select {
	case resp := <-done:
		if resp == nil || string(resp) != `{"reply":"once"}` {
			t.Fatalf("resp %s", resp)
		}
	case <-time.After(2 * time.Second):
		t.Fatal("timeout")
	}
}

func TestSessionEndResolvesInbox(t *testing.T) {
	d, inboxStore, _ := testHookEnv(t)
	_ = inboxStore.Add(wire.Item{ID: "i1", Agent: "t1", Kind: "approval", Title: "t", CreatedMs: inbox.NowMs()})
	_, ok := d.Handle(context.Background(), "claude", "t1", "SessionEnd", json.RawMessage(`{}`))
	if !ok {
		t.Fatal("session end")
	}
	got, _ := inboxStore.Get("i1")
	if !got.Resolved {
		t.Fatal("expected resolved")
	}
}
