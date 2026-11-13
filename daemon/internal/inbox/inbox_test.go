package inbox_test

import (
	"os"
	"path/filepath"
	"testing"

	"sh.simple/simpled/internal/config"
	"sh.simple/simpled/internal/inbox"
	"sh.simple/simpled/internal/wire"
)

func testStore(t *testing.T) (*inbox.Store, func()) {
	t.Helper()
	dir := t.TempDir()
	cfg := &config.Store{StateDir: dir}
	var events []string
	s := inbox.New(cfg, func(string, any) {})
	return s, func() {
		_ = events
	}
}

func TestAddGetAnswer(t *testing.T) {
	s, _ := testStore(t)
	item := wire.Item{ID: "i_test", Agent: "a1", Kind: "approval", Title: "Run", CreatedMs: inbox.NowMs()}
	if err := s.Add(item); err != nil {
		t.Fatal(err)
	}
	got, ok := s.Get("i_test")
	if !ok || got.Title != "Run" {
		t.Fatalf("get %+v ok=%v", got, ok)
	}
	if err := s.Answer("i_test", "allow", nil, nil); err != nil {
		t.Fatal(err)
	}
	got, _ = s.Get("i_test")
	if !got.Resolved {
		t.Fatal("expected resolved")
	}
}

func TestResolveForAgent(t *testing.T) {
	s, _ := testStore(t)
	_ = s.Add(wire.Item{ID: "i1", Agent: "a1", Kind: "question", Title: "Q", CreatedMs: inbox.NowMs()})
	_ = s.Add(wire.Item{ID: "i2", Agent: "a2", Kind: "question", Title: "Q2", CreatedMs: inbox.NowMs()})
	s.ResolveForAgent("a1")
	i1, _ := s.Get("i1")
	i2, _ := s.Get("i2")
	if !i1.Resolved || i2.Resolved {
		t.Fatalf("resolve a1 only i1=%v i2=%v", i1.Resolved, i2.Resolved)
	}
}

func TestTrimMaxItems(t *testing.T) {
	s, _ := testStore(t)
	for i := 0; i < 510; i++ {
		id, _ := inbox.NewItemID()
		_ = s.Add(wire.Item{ID: id, Agent: "a", Kind: "done", Title: "t", CreatedMs: int64(i)})
	}
	list, err := s.List()
	if err != nil {
		t.Fatal(err)
	}
	if len(list) != 500 {
		t.Fatalf("len %d", len(list))
	}
	if list[0].CreatedMs != 10 {
		t.Fatalf("oldest kept %d", list[0].CreatedMs)
	}
}

func TestPersistReload(t *testing.T) {
	dir := t.TempDir()
	cfg := &config.Store{StateDir: dir}
	s1 := inbox.New(cfg, func(string, any) {})
	_ = s1.Add(wire.Item{ID: "i_x", Agent: "a", Kind: "approval", Title: "Cmd", CreatedMs: 1})
	s2 := inbox.New(cfg, func(string, any) {})
	got, ok := s2.Get("i_x")
	if !ok || got.Title != "Cmd" {
		t.Fatalf("reload %+v", got)
	}
	if _, err := os.Stat(filepath.Join(dir, "inbox.json")); err != nil {
		t.Fatal(err)
	}
}

func TestResolveMissingAgents(t *testing.T) {
	s, _ := testStore(t)
	_ = s.Add(wire.Item{ID: "i1", Agent: "gone", Kind: "prompt", Title: "Q", CreatedMs: inbox.NowMs()})
	_ = s.Add(wire.Item{ID: "i2", Agent: "live", Kind: "prompt", Title: "Q2", CreatedMs: inbox.NowMs()})
	s.ResolveMissingAgents(map[string]bool{"live": true})
	i1, _ := s.Get("i1")
	i2, _ := s.Get("i2")
	if !i1.Resolved || i2.Resolved {
		t.Fatalf("gone=%v live=%v", i1.Resolved, i2.Resolved)
	}
}

func TestUserStopSkipsNextDoneOnly(t *testing.T) {
	s, done := testStore(t)
	defer done()
	s.MarkStopped("t1")
	_ = s.Add(wire.Item{ID: "a", Agent: "t1", Kind: "done", CreatedMs: inbox.NowMs()})
	_ = s.Add(wire.Item{ID: "b", Agent: "t2", Kind: "done", CreatedMs: inbox.NowMs()})
	_ = s.Add(wire.Item{ID: "c", Agent: "t1", Kind: "done", CreatedMs: inbox.NowMs()})
	if _, ok := s.Get("a"); ok {
		t.Fatal("done after a user stop must not be added")
	}
	for _, id := range []string{"b", "c"} {
		if _, ok := s.Get(id); !ok {
			t.Fatalf("item %s missing", id)
		}
	}
}
