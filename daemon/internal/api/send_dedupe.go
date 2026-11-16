package api

import (
	"context"
	"encoding/json"
	"sync"
	"time"
)

const sendDedupeTTL = 15 * time.Minute

type sendDedupe struct {
	mu      sync.Mutex
	seen    map[string]time.Time
	byAgent map[string]string
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

func (d *sendDedupe) bind(agentID, key string) {
	if agentID == "" || key == "" {
		return
	}
	d.mu.Lock()
	defer d.mu.Unlock()
	if d.byAgent == nil {
		d.byAgent = map[string]string{}
	}
	d.byAgent[agentID] = key
}

func (d *sendDedupe) releaseAgent(agentID string) {
	if agentID == "" {
		return
	}
	d.mu.Lock()
	defer d.mu.Unlock()
	key, ok := d.byAgent[agentID]
	if !ok {
		return
	}
	delete(d.byAgent, agentID)
	delete(d.seen, key)
}

func sendClientID(p json.RawMessage) string {
	var in struct {
		CID string `json:"cid"`
	}
	_ = json.Unmarshal(p, &in)
	return in.CID
}

func sendAgentID(p json.RawMessage) string {
	var in struct {
		ID string `json:"id"`
	}
	_ = json.Unmarshal(p, &in)
	return in.ID
}

func sendReleaseKey(ctx context.Context) string {
	key, _ := ctx.Value(sendKeyCtx{}).(string)
	return key
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
	sess.srv.sends.bind(sendAgentID(p), key)
	return context.WithValue(ctx, sendKeyCtx{}, key), true
}

func (sess *session) releaseSend(ctx context.Context) {
	if key, ok := ctx.Value(sendKeyCtx{}).(string); ok {
		sess.srv.sends.release(key)
	}
}
