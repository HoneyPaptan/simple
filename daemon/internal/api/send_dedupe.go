package api

import (
	"context"
	"encoding/json"
	"sync"
	"time"
)

const sendDedupeTTL = 15 * time.Minute

type sendDedupe struct {
	mu   sync.Mutex
	seen map[string]time.Time
}

type sendKeyCtx struct{}

func (d *sendDedupe) claim(key string, now time.Time) bool {
	d.mu.Lock()
	defer d.mu.Unlock()
	if d.seen == nil {
		d.seen = map[string]time.Time{}
	}
	for k, t := range d.seen {
		if now.Sub(t) > sendDedupeTTL {
			delete(d.seen, k)
		}
	}
	if _, ok := d.seen[key]; ok {
		return false
	}
	d.seen[key] = now
	return true
}

func (d *sendDedupe) release(key string) {
	d.mu.Lock()
	delete(d.seen, key)
	d.mu.Unlock()
}

func sendClientID(p json.RawMessage) string {
	var in struct {
		CID string `json:"cid"`
	}
	_ = json.Unmarshal(p, &in)
	return in.CID
}

func (sess *session) claimSend(ctx context.Context, p json.RawMessage) (context.Context, bool) {
	cid := sendClientID(p)
	if cid == "" {
		return ctx, true
	}
	key := sess.deviceID + ":" + cid
	if !sess.srv.sends.claim(key, time.Now()) {
		return ctx, false
	}
	return context.WithValue(ctx, sendKeyCtx{}, key), true
}

func (sess *session) releaseSend(ctx context.Context) {
	if key, ok := ctx.Value(sendKeyCtx{}).(string); ok {
		sess.srv.sends.release(key)
	}
}
