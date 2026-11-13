package transcript

import (
	"strings"

	"sh.simple/simpled/internal/wire"
)

const (
	codexContextBaseline    = 12000
	claudeLongContextWindow = 1_000_000
)

func ContextFromClaudeJSONL(path string, agent wire.Agent) (*wire.AgentContext, error) {
	used, model, ok, err := lastClaudeContextUsed(path)
	if err != nil || !ok {
		return nil, err
	}
	window := claudeContextWindow(agent.Model, model)
	if used > window {
		window = claudeLongContextWindow
	}
	if window <= 0 {
		return nil, nil
	}
	return &wire.AgentContext{Used: used, Window: window}, nil
}

func claudeContextWindow(agentModel, lineModel *string) int {
	names := make([]string, 0, 2)
	if lineModel != nil && *lineModel != "" {
		names = append(names, *lineModel)
	}
	if agentModel != nil && *agentModel != "" {
		names = append(names, *agentModel)
	}
	for _, n := range names {
		if strings.Contains(strings.ToLower(n), "[1m]") {
			return claudeLongContextWindow
		}
	}
	return 200_000
}

func ContextFromCodexRollout(path string) (*wire.AgentContext, error) {
	usage, window, ok, err := lastCodexContextUsage(path)
	if err != nil || !ok || window <= codexContextBaseline {
		return nil, err
	}
	total := codexTokensInContext(usage)
	effWindow := window - codexContextBaseline
	used := total - codexContextBaseline
	if used < 0 {
		used = 0
	}
	if used > effWindow {
		used = effWindow
	}
	return &wire.AgentContext{Used: used, Window: effWindow}, nil
}

func codexTokensInContext(usage map[string]any) int {
	if v, ok := intFromAny(usage["total_tokens"]); ok && v > 0 {
		return v
	}
	sum := 0
	for _, k := range []string{"input_tokens", "cached_input_tokens", "cache_write_input_tokens", "output_tokens", "reasoning_output_tokens"} {
		if v, ok := intFromAny(usage[k]); ok {
			sum += v
		}
	}
	return sum
}

func intFromAny(v any) (int, bool) {
	switch n := v.(type) {
	case float64:
		return int(n), true
	case int:
		return n, true
	case int64:
		return int(n), true
	default:
		return 0, false
	}
}

func sameAgentContext(a, b *wire.AgentContext) bool {
	if a == nil && b == nil {
		return true
	}
	if a == nil || b == nil {
		return false
	}
	return a.Used == b.Used && a.Window == b.Window
}
