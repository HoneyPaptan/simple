package herdr

import (
	"bufio"
	"context"
	"encoding/json"
	"net"
	"sync"
	"time"
)

type Feed struct {
	client   *Client
	interval time.Duration
	onChange func([]AgentInfo)
	pollMu   sync.Mutex
	last     map[string]agentRev
}

type agentRev struct {
	revision       uint64
	stateChangeSeq uint64
}

func NewFeed(client *Client, onChange func([]AgentInfo)) *Feed {
	return &Feed{
		client:   client,
		interval: time.Second,
		onChange: onChange,
		last:     make(map[string]agentRev),
	}
}

func (f *Feed) Run(ctx context.Context) {
	backoff := 200 * time.Millisecond
	for {
		select {
		case <-ctx.Done():
			return
		default:
		}
		conn, err := f.client.dial(ctx)
		if err != nil {
			f.client.setStatus(StatusDown)
			sleep(ctx, backoff)
			backoff = growBackoff(backoff)
			f.pollOnce(ctx)
			continue
		}
		subConn := conn
		if err := f.subscribe(subConn); err != nil {
			conn.Close()
			f.client.setStatus(StatusDown)
			sleep(ctx, backoff)
			backoff = growBackoff(backoff)
			f.pollOnce(ctx)
			continue
		}
		f.client.setStatus(StatusUp)
		backoff = 200 * time.Millisecond
		f.pollOnce(ctx)
		if !f.follow(ctx, subConn) {
			return
		}
		f.client.setStatus(StatusDown)
	}
}

func (f *Feed) follow(ctx context.Context, conn net.Conn) bool {
	defer conn.Close()
	notes := make(chan struct{}, 1)
	readErr := make(chan error, 1)
	go func() {
		br := bufio.NewReader(conn)
		for {
			line, err := br.ReadBytes('\n')
			if err != nil {
				readErr <- err
				return
			}
			var note struct {
				Method string `json:"method"`
			}
			if json.Unmarshal(line, &note) != nil {
				continue
			}
			select {
			case notes <- struct{}{}:
			default:
			}
		}
	}()
	ticker := time.NewTicker(f.interval)
	defer ticker.Stop()
	for {
		select {
		case <-ctx.Done():
			return false
		case <-readErr:
			return true
		case <-ticker.C:
			f.pollOnce(ctx)
		case <-notes:
			f.pollOnce(ctx)
		}
	}
}

func (f *Feed) subscribe(conn net.Conn) error {
	params := map[string]any{
		"subscriptions": []map[string]any{
			{"type": "layout.updated"},
			{"type": "pane.agent_detected"},
			{"type": "pane.closed"},
		},
	}
	return f.client.callOn(context.Background(), conn, "events.subscribe", params, nil)
}

func (f *Feed) pollOnce(ctx context.Context) {
	f.pollMu.Lock()
	defer f.pollMu.Unlock()
	list, err := f.client.AgentList(ctx)
	if err != nil {
		f.client.setStatus(StatusDown)
		return
	}
	if !f.changed(list) {
		return
	}
	if f.onChange != nil {
		f.onChange(list)
	}
}

func (f *Feed) changed(list []AgentInfo) bool {
	next := make(map[string]agentRev, len(list))
	changed := len(f.last) != len(list)
	for _, a := range list {
		if a.Agent == "" {
			continue
		}
		id := a.PaneID
		next[id] = agentRev{revision: a.Revision, stateChangeSeq: a.StateChangeSeq}
		old, ok := f.last[id]
		if !ok || old.revision != a.Revision || old.stateChangeSeq != a.StateChangeSeq {
			changed = true
		}
	}
	for id := range f.last {
		if _, ok := next[id]; !ok {
			changed = true
		}
	}
	f.last = next
	return changed
}

func sleep(ctx context.Context, d time.Duration) {
	t := time.NewTimer(d)
	defer t.Stop()
	select {
	case <-ctx.Done():
	case <-t.C:
	}
}

func growBackoff(d time.Duration) time.Duration {
	if d < 5*time.Second {
		d *= 2
		if d > 5*time.Second {
			d = 5 * time.Second
		}
	}
	return d
}
