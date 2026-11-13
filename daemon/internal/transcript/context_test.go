package transcript_test

import (
	"path/filepath"
	"runtime"
	"testing"

	"sh.simple/simpled/internal/transcript"
	"sh.simple/simpled/internal/wire"
)

func fixturePath(name string) string {
	_, file, _, ok := runtime.Caller(0)
	if !ok {
		panic("caller")
	}
	return filepath.Join(filepath.Dir(file), "testdata", name)
}

func TestContextFromClaudeJSONL(t *testing.T) {
	agent := wire.Agent{Model: strPtr("sonnet")}
	ctx, err := transcript.ContextFromClaudeJSONL(fixturePath("context_claude.jsonl"), agent)
	if err != nil {
		t.Fatal(err)
	}
	if ctx == nil || ctx.Used != 1510 || ctx.Window != 200_000 {
		t.Fatalf("%+v", ctx)
	}
}

func TestContextFromClaudeJSONL1M(t *testing.T) {
	agent := wire.Agent{}
	ctx, err := transcript.ContextFromClaudeJSONL(fixturePath("context_claude_1m.jsonl"), agent)
	if err != nil {
		t.Fatal(err)
	}
	if ctx == nil || ctx.Window != 1_000_000 || ctx.Used != 100 {
		t.Fatalf("%+v", ctx)
	}
}

func TestContextFromCodexRollout(t *testing.T) {
	ctx, err := transcript.ContextFromCodexRollout(fixturePath("context_codex.jsonl"))
	if err != nil {
		t.Fatal(err)
	}
	if ctx == nil {
		t.Fatal("nil")
	}
	wantWindow := 272000 - 12000
	wantUsed := 50000 - 12000
	if ctx.Window != wantWindow || ctx.Used != wantUsed {
		t.Fatalf("got used %d window %d", ctx.Used, ctx.Window)
	}
}

func strPtr(s string) *string {
	return &s
}

func TestContextFromClaudeJSONLPast200k(t *testing.T) {
	ctx, err := transcript.ContextFromClaudeJSONL(fixturePath("context_claude_long.jsonl"), wire.Agent{})
	if err != nil {
		t.Fatal(err)
	}
	if ctx == nil || ctx.Window != 1_000_000 || ctx.Used <= 200_000 {
		t.Fatalf("%+v", ctx)
	}
}
