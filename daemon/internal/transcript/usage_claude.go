package transcript

import (
	"bufio"
	"encoding/json"
	"os"
	"strings"
)

func scanClaudeUsage(path string) ([]UsageEvent, error) {
	f, err := os.Open(path)
	if err != nil {
		return nil, err
	}
	defer f.Close()
	var out []UsageEvent
	byMessage := map[string]int{}
	br := bufio.NewScanner(f)
	br.Buffer(make([]byte, 0, 64*1024), maxLineBytes)
	for br.Scan() {
		line := strings.TrimSpace(br.Text())
		if line == "" {
			continue
		}
		var row struct {
			Type      string   `json:"type"`
			Timestamp string   `json:"timestamp"`
			RequestID string   `json:"requestId"`
			CostUSD   *float64 `json:"costUSD"`
			Message   struct {
				ID    string `json:"id"`
				Usage struct {
					InputTokens              int64    `json:"input_tokens"`
					OutputTokens             int64    `json:"output_tokens"`
					CacheCreationInputTokens int64    `json:"cache_creation_input_tokens"`
					CacheReadInputTokens     int64    `json:"cache_read_input_tokens"`
					CostUSD                  *float64 `json:"cost_usd"`
				} `json:"usage"`
				TotalCostUSD *float64 `json:"total_cost_usd"`
			} `json:"message"`
		}
		if json.Unmarshal([]byte(line), &row) != nil {
			continue
		}
		if row.Type != "assistant" {
			continue
		}
		u := row.Message.Usage
		if u.InputTokens == 0 && u.OutputTokens == 0 && u.CacheReadInputTokens == 0 && u.CacheCreationInputTokens == 0 {
			continue
		}
		ts := parseTS(row.Timestamp)
		ev := UsageEvent{
			TsMs:       ts,
			Input:      u.InputTokens,
			Output:     u.OutputTokens,
			CacheRead:  u.CacheReadInputTokens,
			CacheWrite: u.CacheCreationInputTokens,
		}
		if row.Message.TotalCostUSD != nil {
			ev.CostUSD = row.Message.TotalCostUSD
		} else if u.CostUSD != nil {
			ev.CostUSD = u.CostUSD
		} else if row.CostUSD != nil {
			ev.CostUSD = row.CostUSD
		}
		if row.Message.ID != "" {
			key := row.Message.ID + ":" + row.RequestID
			if i, ok := byMessage[key]; ok {
				out[i] = ev
				continue
			}
			byMessage[key] = len(out)
		}
		out = append(out, ev)
	}
	return out, br.Err()
}
