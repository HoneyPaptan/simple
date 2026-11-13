package transcript

import (
	"bufio"
	"encoding/json"
	"os"
	"strings"
)

func scanCodexUsage(path string) ([]UsageEvent, error) {
	useRecord, err := codexFileUsesUsageRecords(path)
	if err != nil {
		return nil, err
	}
	f, err := os.Open(path)
	if err != nil {
		return nil, err
	}
	defer f.Close()
	var events []UsageEvent
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
		if useRecord {
			if typ != "token_usage_record" {
				continue
			}
			if ev, ok := usageEventFromCodexRecord(row); ok {
				events = append(events, ev)
			}
			continue
		}
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
		if ev, ok := usageEventFromCodexUsageMap(row, usageRaw); ok {
			events = append(events, ev)
		}
	}
	return events, br.Err()
}

func codexFileUsesUsageRecords(path string) (bool, error) {
	f, err := os.Open(path)
	if err != nil {
		return false, err
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
		if typ, _ := row["type"].(string); typ == "token_usage_record" {
			return true, br.Err()
		}
	}
	return false, br.Err()
}

func usageEventFromCodexRecord(row map[string]any) (UsageEvent, bool) {
	payload, _ := row["payload"].(map[string]any)
	if payload == nil {
		return UsageEvent{}, false
	}
	usageRaw, _ := payload["usage"].(map[string]any)
	if usageRaw == nil {
		usageRaw, _ = payload["turn_token_usage"].(map[string]any)
	}
	if usageRaw == nil {
		return UsageEvent{}, false
	}
	return usageEventFromCodexUsageMap(row, usageRaw)
}

func usageEventFromCodexUsageMap(row map[string]any, usageRaw map[string]any) (UsageEvent, bool) {
	in := int64FromAny(usageRaw["input_tokens"])
	outTok := int64FromAny(usageRaw["output_tokens"])
	cacheRead := int64FromAny(usageRaw["cached_input_tokens"])
	cacheWrite := int64FromAny(usageRaw["cache_write_input_tokens"])
	if in == 0 && outTok == 0 && cacheRead == 0 && cacheWrite == 0 {
		return UsageEvent{}, false
	}
	ts := codexLineTS(row)
	ev := UsageEvent{TsMs: ts, Input: in, Output: outTok, CacheRead: cacheRead, CacheWrite: cacheWrite}
	if c := floatFromAny(usageRaw["cost_usd"]); c != nil {
		ev.CostUSD = c
	}
	return ev, true
}

func int64FromAny(v any) int64 {
	switch n := v.(type) {
	case float64:
		return int64(n)
	case json.Number:
		i, _ := n.Int64()
		return i
	default:
		return 0
	}
}

func floatFromAny(v any) *float64 {
	switch n := v.(type) {
	case float64:
		return &n
	default:
		return nil
	}
}
