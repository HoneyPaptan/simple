package usage_test

import (
	"fmt"
	"os"
	"path/filepath"
	"testing"
	"time"

	"sh.simple/simpled/internal/usage"
)

func TestSummaryFromTranscriptDirs(t *testing.T) {
	home := t.TempDir()
	now := time.Now().UTC()
	ts := now.Format("2006-01-02T15:04:05.000Z")
	line := fmt.Sprintf(`{"type":"assistant","timestamp":"%s","message":{"usage":{"input_tokens":10,"output_tokens":5}}}`, ts)
	claudeDir := filepath.Join(home, ".claude", "projects", "p1")
	if err := os.MkdirAll(claudeDir, 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(claudeDir, "a.jsonl"), []byte(line), 0o644); err != nil {
		t.Fatal(err)
	}
	sum := usage.NewSummarizer().Summary(home, 7)
	if sum.Today.Input != 10 {
		t.Fatalf("today %+v", sum.Today)
	}
	found := false
	for _, row := range sum.Agents {
		if row.Kind == "claude" && row.HasData {
			found = true
		}
	}
	if !found {
		t.Fatalf("agents %+v", sum.Agents)
	}
}
