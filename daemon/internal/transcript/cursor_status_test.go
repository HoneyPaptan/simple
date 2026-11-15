package transcript

import (
	"os"
	"path/filepath"
	"testing"
	"time"

	"sh.simple/simpled/internal/agents"
	"sh.simple/simpled/internal/wire"
)

func TestCursorScanTailStates(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "t.jsonl")
	write := func(s string) {
		if err := os.WriteFile(path, []byte(s), 0o644); err != nil {
			t.Fatal(err)
		}
	}
	write(`{"role":"user","message":{"content":[{"type":"text","text":"<user_query>\nhi\n</user_query>"}]}}
`)
	tail, err := cursorScanTail(path)
	if err != nil || !tail.PendingTools {
		t.Fatalf("user tail %#v err %v", tail, err)
	}
	write(`{"role":"user","message":{"content":[{"type":"text","text":"<user_query>\nhi\n</user_query>"}]}}
{"role":"assistant","message":{"content":[{"type":"tool_use","name":"Read","input":{"path":"/x"}}]}}
`)
	tail, err = cursorScanTail(path)
	if err != nil || !tail.PendingTools || tail.TextOnlyAssistant {
		t.Fatalf("tools %#v", tail)
	}
	write(`{"role":"user","message":{"content":[{"type":"text","text":"<user_query>\nhi\n</user_query>"}]}}
{"role":"assistant","message":{"content":[{"type":"text","text":"done reply\n\n[REDACTED]"}]}}
`)
	tail, err = cursorScanTail(path)
	if err != nil || !tail.TextOnlyAssistant || tail.PendingTools || tail.LastAssistantText != "done reply" {
		t.Fatalf("done tail %#v", tail)
	}
}

func TestPollCursorStatusDoneOnce(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "t.jsonl")
	body := `{"role":"user","message":{"content":[{"type":"text","text":"<user_query>\nask\n</user_query>"}]}}
{"role":"assistant","message":{"content":[{"type":"text","text":"answer"}]}}
`
	if err := os.WriteFile(path, []byte(body), 0o644); err != nil {
		t.Fatal(err)
	}
	var statuses []string
	var doneBodies []string
	m := NewManager(func(string, any) {})
	m.SetCursorLifecycle(func(id, st string) { statuses = append(statuses, st) }, func(id, body string) { doneBodies = append(doneBodies, body) })
	now := time.Now()
	info, _ := os.Stat(path)
	m.pollCursorStatus("a1", path, info.Size(), now)
	if len(statuses) != 1 || statuses[0] != "working" {
		t.Fatalf("first statuses %v", statuses)
	}
	m.pollCursorStatus("a1", path, info.Size(), now.Add(3*time.Second))
	if len(statuses) != 2 || statuses[len(statuses)-1] != "done" {
		t.Fatalf("statuses %v", statuses)
	}
	if len(doneBodies) != 1 || doneBodies[0] != "answer" {
		t.Fatalf("done %v", doneBodies)
	}
	m.pollCursorStatus("a1", path, info.Size(), now.Add(5*time.Second))
	if len(doneBodies) != 1 {
		t.Fatalf("duplicate done %v", doneBodies)
	}
}

func TestPollCursorStatusSkipsTurnFinishedBeforeWatching(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "t.jsonl")
	body := `{"role":"user","message":{"content":[{"type":"text","text":"<user_query>\nask\n</user_query>"}]}}
{"role":"assistant","message":{"content":[{"type":"text","text":"answer"}]}}
`
	if err := os.WriteFile(path, []byte(body), 0o644); err != nil {
		t.Fatal(err)
	}
	old := time.Now().Add(-time.Hour)
	if err := os.Chtimes(path, old, old); err != nil {
		t.Fatal(err)
	}
	var doneBodies []string
	m := NewManager(func(string, any) {})
	m.SetCursorLifecycle(func(string, string) {}, func(id, body string) { doneBodies = append(doneBodies, body) })
	info, _ := os.Stat(path)
	now := time.Now()
	m.pollCursorStatus("a1", path, info.Size(), now)
	m.pollCursorStatus("a1", path, info.Size(), now.Add(3*time.Second))
	if len(doneBodies) != 0 {
		t.Fatalf("old turn announced %v", doneBodies)
	}
}

func TestPollCursorStatusQuietRestartKeepsUpdatedMs(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "t.jsonl")
	body := `{"role":"user","message":{"content":[{"type":"text","text":"<user_query>\nask\n</user_query>"}]}}
{"role":"assistant","message":{"content":[{"type":"text","text":"answer"}]}}
`
	if err := os.WriteFile(path, []byte(body), 0o644); err != nil {
		t.Fatal(err)
	}
	old := time.Now().Add(-time.Hour)
	if err := os.Chtimes(path, old, old); err != nil {
		t.Fatal(err)
	}
	s := agents.NewStore(func(string, any) {})
	s.Upsert(wire.Agent{ID: "a1", Kind: "cursor", Status: "unknown", StartedMs: 1000, UpdatedMs: 1000})
	m := NewManager(func(string, any) {})
	m.SetCursorLifecycle(func(id, st string) { s.SetAgentFields(id, st, nil) }, func(string, string) {})
	info, _ := os.Stat(path)
	now := time.Now()
	m.pollCursorStatus("a1", path, info.Size(), now)
	m.pollCursorStatus("a1", path, info.Size(), now.Add(3*time.Second))
	got, _ := s.Get("a1")
	if got.UpdatedMs != 1000 || got.Status != "unknown" {
		t.Fatalf("quiet restart rewrote status %s updated %d", got.Status, got.UpdatedMs)
	}
	next := body + `{"role":"user","message":{"content":[{"type":"text","text":"<user_query>\nagain\n</user_query>"}]}}
`
	if err := os.WriteFile(path, []byte(next), 0o644); err != nil {
		t.Fatal(err)
	}
	info, _ = os.Stat(path)
	m.pollCursorStatus("a1", path, info.Size(), now.Add(4*time.Second))
	got, _ = s.Get("a1")
	if got.Status != "working" || got.UpdatedMs == 1000 {
		t.Fatalf("real change status %s updated %d", got.Status, got.UpdatedMs)
	}
}
