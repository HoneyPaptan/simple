package transcript

import (
	"encoding/json"
	"testing"

	"sh.simple/simpled/internal/opencode"
)

func TestOpenCodeAssistantTokensUsed(t *testing.T) {
	raw := `{"type":"assistant","model":{"id":"gpt-5","providerID":"openai"},"tokens":{"input":1000,"output":200,"reasoning":50,"cache":{"read":10,"write":5}}}`
	var row openCodeAssistant
	if json.Unmarshal([]byte(raw), &row) != nil {
		t.Fatal("json")
	}
	tok := row.Tokens
	used := tok.Input + tok.Output + tok.Reasoning + tok.Cache.Read + tok.Cache.Write
	if used != 1265 {
		t.Fatalf("%d", used)
	}
}

func TestOpenCodeTokensSurviveItemRoundTrip(t *testing.T) {
	raw := `{"id":"m1","type":"assistant","model":{"id":"big-pickle","providerID":"opencode"},"tokens":{"input":419,"output":2,"reasoning":0,"cache":{"read":32256,"write":0}}}`
	var item opencode.Item
	if json.Unmarshal([]byte(raw), &item) != nil {
		t.Fatal("item json")
	}
	again, _ := json.Marshal(item)
	var row openCodeAssistant
	if json.Unmarshal(again, &row) != nil || row.Tokens == nil || row.Model == nil {
		t.Fatalf("tokens or model lost: %s", again)
	}
	if got := row.Tokens.Input + row.Tokens.Cache.Read; got != 32675 {
		t.Fatalf("used %d", got)
	}
	if row.Model.ProviderID != "opencode" || row.Model.ID != "big-pickle" {
		t.Fatalf("model %+v", row.Model)
	}
}
