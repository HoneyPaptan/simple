package transcript

import (
	"bufio"
	"encoding/json"
	"os"
	"strings"
)

func lastClaudeContextUsed(path string) (used int, model *string, ok bool, err error) {
	f, err := os.Open(path)
	if err != nil {
		return 0, nil, false, err
	}
	defer f.Close()
	br := bufio.NewScanner(f)
	br.Buffer(make([]byte, 0, 64*1024), maxLineBytes)
	var lastLine string
	for br.Scan() {
		line := strings.TrimSpace(br.Text())
		if line == "" {
			continue
		}
		var probe struct {
			Type string `json:"type"`
		}
		if json.Unmarshal([]byte(line), &probe) != nil || probe.Type != "assistant" {
			continue
		}
		lastLine = line
	}
	if err := br.Err(); err != nil {
		return 0, nil, false, err
	}
	if lastLine == "" {
		return 0, nil, false, nil
	}
	var row struct {
		Message struct {
			Model *string `json:"model"`
			Usage struct {
				InputTokens              int `json:"input_tokens"`
				CacheCreationInputTokens int `json:"cache_creation_input_tokens"`
				CacheReadInputTokens     int `json:"cache_read_input_tokens"`
			} `json:"usage"`
		} `json:"message"`
	}
	if json.Unmarshal([]byte(lastLine), &row) != nil {
		return 0, nil, false, nil
	}
	u := row.Message.Usage
	total := u.InputTokens + u.CacheCreationInputTokens + u.CacheReadInputTokens
	if total <= 0 {
		return 0, nil, false, nil
	}
	return total, row.Message.Model, true, nil
}
