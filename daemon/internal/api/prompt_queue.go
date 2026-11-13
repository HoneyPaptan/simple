package api

import (
	"context"
	"errors"
	"sync"
	"time"
)

const maxQueuedPrompts = 20

var errPromptQueueFull = errors.New("too many queued messages")

type queuedPrompt struct {
	text   string
	device string
}

type promptQueue struct {
	mu         sync.Mutex
	pending    map[string][]queuedPrompt
	lastSent   map[string]time.Time
	busy       func(id string) bool
	authorized func(device string) bool
	deliver    func(ctx context.Context, id, text string) error
	poll       time.Duration
	settle     time.Duration
}

func newPromptQueue(busy func(string) bool, authorized func(string) bool, deliver func(context.Context, string, string) error) *promptQueue {
	return &promptQueue{
		pending:    make(map[string][]queuedPrompt),
		lastSent:   make(map[string]time.Time),
		busy:       busy,
		authorized: authorized,
		deliver:    deliver,
		poll:       700 * time.Millisecond,
		settle:     5 * time.Second,
	}
}

func (q *promptQueue) Submit(ctx context.Context, id, text, device string) (bool, error) {
	q.mu.Lock()
	if len(q.pending[id]) == 0 && !q.blockedLocked(id) {
		q.lastSent[id] = time.Now()
		q.mu.Unlock()
		return false, q.deliver(ctx, id, text)
	}
	if len(q.pending[id]) >= maxQueuedPrompts {
		q.mu.Unlock()
		return false, errPromptQueueFull
	}
	start := len(q.pending[id]) == 0
	q.pending[id] = append(q.pending[id], queuedPrompt{text: text, device: device})
	q.mu.Unlock()
	if start {
		go q.drain(id)
	}
	return true, nil
}

func (q *promptQueue) Pending(id string) []string {
	q.mu.Lock()
	defer q.mu.Unlock()
	out := make([]string, 0, len(q.pending[id]))
	for _, p := range q.pending[id] {
		out = append(out, p.text)
	}
	return out
}

func (q *promptQueue) Clear(id string) {
	q.mu.Lock()
	defer q.mu.Unlock()
	delete(q.pending, id)
}

func (q *promptQueue) DropDevice(device string) {
	q.mu.Lock()
	defer q.mu.Unlock()
	for id, list := range q.pending {
		kept := list[:0]
		for _, p := range list {
			if p.device != device {
				kept = append(kept, p)
			}
		}
		if len(kept) == 0 {
			delete(q.pending, id)
		} else {
			q.pending[id] = kept
		}
	}
}

func (q *promptQueue) blockedLocked(id string) bool {
	return q.busy(id) || time.Since(q.lastSent[id]) < q.settle
}

func (q *promptQueue) next(id string) (queuedPrompt, bool) {
	for {
		q.mu.Lock()
		if len(q.pending[id]) == 0 {
			q.mu.Unlock()
			return queuedPrompt{}, false
		}
		if !q.blockedLocked(id) {
			p := q.pending[id][0]
			q.pending[id] = q.pending[id][1:]
			if len(q.pending[id]) == 0 {
				delete(q.pending, id)
			}
			q.lastSent[id] = time.Now()
			q.mu.Unlock()
			return p, true
		}
		q.mu.Unlock()
		time.Sleep(q.poll)
	}
}

func (q *promptQueue) drain(id string) {
	ctx := context.Background()
	for {
		p, ok := q.next(id)
		if !ok {
			return
		}
		if q.authorized(p.device) {
			_ = q.deliver(ctx, id, p.text)
		}
	}
}
