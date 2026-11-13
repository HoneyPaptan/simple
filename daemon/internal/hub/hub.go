package hub

import (
	"encoding/json"
	"sync"

	"sh.simple/simpled/internal/wire"
)

const ringSize = 4096

type Event struct {
	Seq int64
	Ev  string
	D   json.RawMessage
}

type Hub struct {
	mu       sync.RWMutex
	epoch    string
	seq      int64
	ring     [ringSize]Event
	subs     map[int]chan Event
	nextSub  int
	subBuf   int
}

func New(epoch string) *Hub {
	return &Hub{
		epoch:   epoch,
		subs:    make(map[int]chan Event),
		subBuf:  64,
	}
}

func (h *Hub) Epoch() string {
	h.mu.RLock()
	defer h.mu.RUnlock()
	return h.epoch
}

func (h *Hub) Seq() int64 {
	h.mu.RLock()
	defer h.mu.RUnlock()
	return h.seq
}

func (h *Hub) Publish(ev string, data any) Event {
	b, _ := json.Marshal(data)
	return h.PublishRaw(ev, b)
}

func (h *Hub) PublishRaw(ev string, d json.RawMessage) Event {
	h.mu.Lock()
	defer h.mu.Unlock()
	h.seq++
	e := Event{Seq: h.seq, Ev: ev, D: d}
	h.ring[int(h.seq%ringSize)] = e
	for id, ch := range h.subs {
		select {
		case ch <- e:
		default:
			close(ch)
			delete(h.subs, id)
		}
	}
	return e
}

func (h *Hub) Since(since int64) ([]Event, bool) {
	h.mu.RLock()
	defer h.mu.RUnlock()
	if since >= h.seq {
		return nil, true
	}
	if since < h.seq-int64(ringSize) {
		return nil, false
	}
	var out []Event
	for s := since + 1; s <= h.seq; s++ {
		out = append(out, h.ring[int(s%ringSize)])
	}
	return out, true
}

func (h *Hub) Subscribe() (<-chan Event, func()) {
	h.mu.Lock()
	defer h.mu.Unlock()
	id := h.nextSub
	h.nextSub++
	ch := make(chan Event, h.subBuf)
	h.subs[id] = ch
	return ch, func() {
		h.mu.Lock()
		defer h.mu.Unlock()
		if c, ok := h.subs[id]; ok {
			close(c)
			delete(h.subs, id)
		}
	}
}

func SnapshotAgents(agents []wire.Agent) *wire.Snapshot {
	return &wire.Snapshot{
		Agents:     agents,
		Inbox:      []wire.Item{},
		Workspaces: []wire.Workspace{},
	}
}
