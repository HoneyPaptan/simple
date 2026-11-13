package agents

import (
	"os"
	"path/filepath"
	"testing"
)

func TestTranscriptPathFindsMovedSessionAndCachesMisses(t *testing.T) {
	cfg := t.TempDir()
	t.Setenv("CLAUDE_CONFIG_DIR", cfg)
	t.Setenv("HOME", t.TempDir())
	cwd := "/work/a"
	fallback := filepath.Join(ClaudeProjectDir(cwd), "sess-cache-1.jsonl")
	if got := TranscriptPath("sess-cache-1", cwd); got != fallback {
		t.Fatalf("missing session = %q, want fallback %q", got, fallback)
	}
	other := filepath.Join(cfg, "projects", "-other", "sess-cache-1.jsonl")
	if err := os.MkdirAll(filepath.Dir(other), 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(other, []byte("{}\n"), 0o600); err != nil {
		t.Fatal(err)
	}
	if got := TranscriptPath("sess-cache-1", cwd); got != fallback {
		t.Fatalf("within the miss window = %q, want cached fallback", got)
	}
	transcriptHits.m["sess-cache-1"] = transcriptHit{}
	if got := TranscriptPath("sess-cache-1", cwd); got != other {
		t.Fatalf("after the miss expired = %q, want %q", got, other)
	}
	if got := TranscriptPath("sess-cache-1", cwd); got != other {
		t.Fatalf("cached hit = %q, want %q", got, other)
	}
}
