package transcript

import (
	"bufio"
	"encoding/json"
	"os"
	"strings"
)

func lastCodexContextUsage(path string) (usage map[string]any, window int, ok bool, err error) {
	f, err := os.Open(path)
	if err != nil {
		return nil, 0, false, err
	}
	defer f.Close()
	br := bufio.NewScanner(f)
	br.Buffer(make([]byte, 0, 64*1024), maxLineBytes)
	for br.Scan() {
		line := strings.TrimSpace(br.Text())
		if line == "" {
			continue
		}
		var row map[string]any
		if json.Unmarshal([]byte(line), &row) != nil {
			continue
		}
		typ, _ := row["type"].(string)
		if typ != "event_msg" {
			continue
		}
		payload, _ := row["payload"].(map[string]any)
		if payload == nil {
			continue
		}
		sub, _ := payload["type"].(string)
		if sub != "token_count" {
			continue
		}
		info, _ := payload["info"].(map[string]any)
		if info == nil {
			continue
		}
		usageRaw, _ := info["last_token_usage"].(map[string]any)
		if usageRaw == nil {
			continue
		}
		usage = usageRaw
		if w, ok := intFromAny(info["model_context_window"]); ok && w > 0 {
			window = w
		}
		ok = true
	}
	return usage, window, ok, br.Err()
}
