package usage_test

import (
	"fmt"
	"os"
	"path/filepath"
	"testing"
	"time"

	"sh.simple/simpled/internal/usage"
)

func TestSummaryFromClaudeFixture(t *testing.T) {
	home := t.TempDir()
	now := time.Now().UTC()
	ts := now.Format("2006-01-02T15:04:05.000Z")
	line := fmt.Sprintf(`{"type":"assistant","uuid":"u2","timestamp":"%s","message":{"role":"assistant","content":[{"type":"text","text":"Done"}],"usage":{"input_tokens":100,"output_tokens":50,"cache_creation_input_tokens":10,"cache_read_input_tokens":20},"total_cost_usd":0.0012}}`, ts)
	claudeDir := filepath.Join(home, ".claude", "projects", "demo")
	if err := os.MkdirAll(claudeDir, 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(claudeDir, "u.jsonl"), []byte(line), 0o644); err != nil {
		t.Fatal(err)
	}
	sum := usage.NewSummarizer().Summary(home, 7)
	if sum.Today.Input != 100 || sum.Period.Output != 50 {
		t.Fatalf("today %+v period %+v", sum.Today, sum.Period)
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
