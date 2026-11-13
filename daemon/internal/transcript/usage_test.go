package transcript_test

import (
	"path/filepath"
	"runtime"
	"testing"

	"sh.simple/simpled/internal/transcript"
)

func TestScanClaudeUsageFixture(t *testing.T) {
	_, file, _, ok := runtime.Caller(0)
	if !ok {
		t.Fatal("caller")
	}
	path := filepath.Join(filepath.Dir(file), "testdata", "usage_claude.jsonl")
	cache := transcript.NewUsageCache()
	ev1, err := cache.Events(path, "claude")
	if err != nil {
		t.Fatal(err)
	}
	if len(ev1) != 1 {
		t.Fatalf("events %d", len(ev1))
	}
	if ev1[0].Input != 100 || ev1[0].Output != 50 || ev1[0].CacheRead != 20 || ev1[0].CacheWrite != 10 {
		t.Fatalf("%+v", ev1[0])
	}
	if ev1[0].CostUSD == nil || *ev1[0].CostUSD != 0.0012 {
		t.Fatalf("cost %+v", ev1[0].CostUSD)
	}
	ev2, err := cache.Events(path, "claude")
	if err != nil {
		t.Fatal(err)
	}
	if len(ev2) != 1 {
		t.Fatalf("cache miss")
	}
}

func TestScanCodexUsageFixture(t *testing.T) {
	_, file, _, ok := runtime.Caller(0)
	if !ok {
		t.Fatal("caller")
	}
	path := filepath.Join(filepath.Dir(file), "testdata", "codex_rollout.jsonl")
	cache := transcript.NewUsageCache()
	ev, err := cache.Events(path, "codex")
	if err != nil {
		t.Fatal(err)
	}
	if len(ev) != 1 {
		t.Fatalf("events %d", len(ev))
	}
	if ev[0].Input != 200 || ev[0].Output != 80 || ev[0].CacheRead != 50 || ev[0].CacheWrite != 5 {
		t.Fatalf("%+v", ev[0])
	}
}

func TestScanCodexTokenCountFixture(t *testing.T) {
	_, file, _, ok := runtime.Caller(0)
	if !ok {
		t.Fatal("caller")
	}
	path := filepath.Join(filepath.Dir(file), "testdata", "codex_token_count.jsonl")
	cache := transcript.NewUsageCache()
	ev, err := cache.Events(path, "codex")
	if err != nil {
		t.Fatal(err)
	}
	if len(ev) != 1 {
		t.Fatalf("events %d", len(ev))
	}
	if ev[0].Input != 90 || ev[0].Output != 40 || ev[0].CacheRead != 10 || ev[0].CacheWrite != 2 {
		t.Fatalf("%+v", ev[0])
	}
}

func TestScanCodexBothShapesNotDoubleCounted(t *testing.T) {
	_, file, _, ok := runtime.Caller(0)
	if !ok {
		t.Fatal("caller")
	}
	path := filepath.Join(filepath.Dir(file), "testdata", "codex_both_shapes.jsonl")
	cache := transcript.NewUsageCache()
	ev, err := cache.Events(path, "codex")
	if err != nil {
		t.Fatal(err)
	}
	if len(ev) != 1 {
		t.Fatalf("events %d", len(ev))
	}
	if ev[0].Input != 30 || ev[0].Output != 12 {
		t.Fatalf("%+v", ev[0])
	}
}

func TestScanClaudeUsageCountsSplitMessageOnce(t *testing.T) {
	_, file, _, _ := runtime.Caller(0)
	path := filepath.Join(filepath.Dir(file), "testdata", "usage_claude_split.jsonl")
	ev, err := transcript.NewUsageCache().Events(path, "claude")
	if err != nil {
		t.Fatal(err)
	}
	var in, out, read int64
	for _, e := range ev {
		in, out, read = in+e.Input, out+e.Output, read+e.CacheRead
	}
	if len(ev) != 2 || in != 30 || out != 47 || read != 300 {
		t.Fatalf("events %d input %d output %d cache read %d", len(ev), in, out, read)
	}
}
