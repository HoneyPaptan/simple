package api

import (
	"context"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"testing"
	"time"

	"sh.simple/simpled/internal/hub"
	"sh.simple/simpled/internal/usage"
	"sh.simple/simpled/internal/wire"
)

func TestDispatchUsageSummary(t *testing.T) {
	home := t.TempDir()
	t.Setenv("HOME", home)
	now := time.Now().UTC()
	ts := now.Format("2006-01-02T15:04:05.000Z")
	line := fmt.Sprintf(`{"type":"assistant","uuid":"u2","timestamp":"%s","message":{"role":"assistant","content":[{"type":"text","text":"Done"}],"usage":{"input_tokens":100,"output_tokens":50,"cache_creation_input_tokens":10,"cache_read_input_tokens":20}}}`, ts)
	claudeDir := filepath.Join(home, ".claude", "projects", "demo")
	if err := os.MkdirAll(claudeDir, 0o755); err != nil {
		t.Fatal(err)
	}
	path := filepath.Join(claudeDir, "sess.jsonl")
	if err := os.WriteFile(path, []byte(line), 0o644); err != nil {
		t.Fatal(err)
	}
	h := hub.New("e_usage")
	srv := New(Options{Hub: h, Usage: usage.NewSummarizer()})
	ctx := context.Background()
	var frame []byte
	sess := &session{srv: srv, helloOK: true, captureWS: func(b []byte) { frame = b }}
	sess.dispatch(ctx, 1, "usage.summary", []byte(`{"days":7}`))
	var out struct {
		R wire.UsageSummaryResult `json:"r"`
	}
	if json.Unmarshal(frame, &out) != nil || out.R.Today.Input != 100 {
		t.Fatalf("frame %s", frame)
	}
	if len(out.R.Agents) < 1 || !out.R.Agents[0].HasData {
		t.Fatalf("agents %+v", out.R.Agents)
	}
}
