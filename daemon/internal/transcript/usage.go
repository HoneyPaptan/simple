package transcript

import (
	"os"
	"sync"
)

type UsageEvent struct {
	TsMs        int64
	Input       int64
	Output      int64
	CacheRead   int64
	CacheWrite  int64
	CostUSD     *float64
}

type UsageCache struct {
	mu      sync.Mutex
	entries map[string]usageCacheEntry
}

type usageCacheEntry struct {
	size   int64
	mtime  int64
	events []UsageEvent
}

func NewUsageCache() *UsageCache {
	return &UsageCache{entries: make(map[string]usageCacheEntry)}
}

func (c *UsageCache) Events(path, kind string) ([]UsageEvent, error) {
	if path == "" {
		return nil, nil
	}
	st, err := os.Stat(path)
	if err != nil {
		if os.IsNotExist(err) {
			return nil, nil
		}
		return nil, err
	}
	c.mu.Lock()
	defer c.mu.Unlock()
	if ent, ok := c.entries[path]; ok && ent.size == st.Size() && ent.mtime == st.ModTime().UnixNano() {
		return ent.events, nil
	}
	var events []UsageEvent
	switch kind {
	case "claude":
		events, err = scanClaudeUsage(path)
	case "codex":
		events, err = scanCodexUsage(path)
	default:
		events = nil
	}
	if err != nil {
		return nil, err
	}
	c.entries[path] = usageCacheEntry{size: st.Size(), mtime: st.ModTime().UnixNano(), events: events}
	return events, nil
}
