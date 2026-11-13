package transcript

import (
	"context"
	"encoding/json"
	"strings"

	"sh.simple/simpled/internal/opencode"
	"sh.simple/simpled/internal/wire"
)

type openCodeTokenUsage struct {
	Input     int `json:"input"`
	Output    int `json:"output"`
	Reasoning int `json:"reasoning"`
	Cache     struct {
		Read  int `json:"read"`
		Write int `json:"write"`
	} `json:"cache"`
}

type openCodeAssistant struct {
	Type  string              `json:"type"`
	Model *opencode.ModelRef  `json:"model"`
	Tokens *openCodeTokenUsage `json:"tokens"`
}

func (m *Manager) contextFromOpenCode(session string, agent wire.Agent) (*wire.AgentContext, string, error) {
	ctx, cancel := context.WithTimeout(context.Background(), openCodeTimeout)
	defer cancel()
	items, err := m.openCode.Messages(ctx, session)
	if err != nil || len(items) == 0 {
		return nil, "", err
	}
	var last openCodeAssistant
	found := false
	for i := len(items) - 1; i >= 0; i-- {
		raw, _ := json.Marshal(items[i])
		var row openCodeAssistant
		if json.Unmarshal(raw, &row) != nil || row.Type != "assistant" || row.Tokens == nil {
			continue
		}
		last = row
		found = true
		break
	}
	if !found {
		return nil, "", nil
	}
	model := ""
	if last.Model != nil && last.Model.ProviderID != "" && last.Model.ID != "" {
		model = last.Model.ProviderID + "/" + last.Model.ID
	}
	t := last.Tokens
	used := t.Input + t.Output + t.Reasoning + t.Cache.Read + t.Cache.Write
	if used <= 0 {
		return nil, model, nil
	}
	window, err := m.openCodeContextWindow(ctx, last.Model, agent)
	if err != nil || window <= 0 {
		return nil, model, err
	}
	return &wire.AgentContext{Used: used, Window: window}, model, nil
}

func (m *Manager) openCodeContextWindow(ctx context.Context, ref *opencode.ModelRef, agent wire.Agent) (int, error) {
	key := openCodeModelKey(ref, agent)
	if key == "" {
		return 0, nil
	}
	if w := m.cachedOpenCodeLimit(key); w > 0 {
		return w, nil
	}
	models, err := m.openCode.Models(ctx)
	if err != nil {
		return 0, err
	}
	m.storeOpenCodeLimits(models)
	return m.cachedOpenCodeLimit(key), nil
}

func openCodeModelKey(ref *opencode.ModelRef, agent wire.Agent) string {
	if ref != nil && ref.ProviderID != "" && ref.ID != "" {
		return ref.ProviderID + "/" + ref.ID
	}
	if agent.Model != nil {
		return strings.TrimSpace(*agent.Model)
	}
	return ""
}

func (m *Manager) cachedOpenCodeLimit(key string) int {
	m.mu.Lock()
	defer m.mu.Unlock()
	return m.openCodeLimits[key]
}

func (m *Manager) storeOpenCodeLimits(models []opencode.Model) {
	m.mu.Lock()
	defer m.mu.Unlock()
	if m.openCodeLimits == nil {
		m.openCodeLimits = make(map[string]int)
	}
	for _, mod := range models {
		key := opencode.ModelKey(mod)
		if mod.Limit.Context > 0 {
			m.openCodeLimits[key] = mod.Limit.Context
		}
	}
}
