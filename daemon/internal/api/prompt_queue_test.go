package api

import (
	"context"
	"errors"
	"sync"
	"sync/atomic"
	"testing"
	"time"
)

type fakeAgent struct {
	mu   sync.Mutex
	busy atomic.Bool
	hold atomic.Bool
	got  []string
}

func (f *fakeAgent) deliver(_ context.Context, _ string, text string) error {
	if f.hold.Load() {
		return errSendHeld
	}
	f.mu.Lock()
	f.got = append(f.got, text)
	f.mu.Unlock()
	return nil
}

func (f *fakeAgent) delivered() []string {
	f.mu.Lock()
	defer f.mu.Unlock()
	return append([]string(nil), f.got...)
}

func testQueue(f *fakeAgent) *promptQueue {
	q := newPromptQueue(func(string) bool { return f.busy.Load() }, func(d string) bool { return d != "revoked" }, f.deliver)
	q.poll = 5 * time.Millisecond
	q.settle = 20 * time.Millisecond
	return q
}

func TestPromptQueueHoldsMessagesWhileBusyAndDeliversEachSeparately(t *testing.T) {
	f := &fakeAgent{}
	f.busy.Store(true)
	q := testQueue(f)
	for _, text := range []string{"first", "second"} {
		queued, err := q.Submit(context.Background(), "a1", text, "phone")
		if err != nil || !queued {
			t.Fatalf("submit %q queued=%v err=%v", text, queued, err)
		}
	}
	time.Sleep(30 * time.Millisecond)
	if got := f.delivered(); len(got) != 0 {
		t.Fatalf("delivered while busy: %v", got)
	}
	if got := q.Pending("a1"); len(got) != 2 {
		t.Fatalf("pending %v", got)
	}
	f.busy.Store(false)
	deadline := time.Now().Add(time.Second)
	for len(f.delivered()) < 2 && time.Now().Before(deadline) {
		time.Sleep(5 * time.Millisecond)
	}
	got := f.delivered()
	if len(got) != 2 || got[0] != "first" || got[1] != "second" {
		t.Fatalf("delivered %v", got)
	}
}

func TestPromptQueueSendsAtOnceWhenIdle(t *testing.T) {
	f := &fakeAgent{}
	q := testQueue(f)
	queued, err := q.Submit(context.Background(), "a1", "hello", "phone")
	if err != nil || queued {
		t.Fatalf("queued=%v err=%v", queued, err)
	}
	if got := f.delivered(); len(got) != 1 {
		t.Fatalf("delivered %v", got)
	}
	queued, _ = q.Submit(context.Background(), "a1", "right after", "phone")
	if !queued {
		t.Fatal("second message within the settle window must queue")
	}
}

func TestPromptQueueHoldsLatchedAuthInsteadOfDelivering(t *testing.T) {
	f := &fakeAgent{}
	f.hold.Store(true)
	q := testQueue(f)
	_, err := q.Submit(context.Background(), "a1", "follow up", "phone")
	if !errors.Is(err, errSendHeld) {
		t.Fatalf("immediate err %v", err)
	}
	if got := f.delivered(); len(got) != 0 {
		t.Fatalf("delivered immediate %v", got)
	}

	released := make(chan string, 1)
	f.busy.Store(true)
	q.release = func(key string) { released <- key }
	ctx := context.WithValue(context.Background(), sendKeyCtx{}, "dev:cid1")
	queued, err := q.Submit(ctx, "a1", "queued follow up", "phone")
	if err != nil || !queued {
		t.Fatalf("queued=%v err=%v", queued, err)
	}
	f.busy.Store(false)
	select {
	case key := <-released:
		if key != "dev:cid1" {
			t.Fatalf("released %q", key)
		}
	case <-time.After(time.Second):
		t.Fatal("queued send was not released")
	}
	if got := f.delivered(); len(got) != 0 {
		t.Fatalf("delivered queued %v", got)
	}
}

func TestPromptQueueDropsRevokedClearedAndCapsLength(t *testing.T) {
	f := &fakeAgent{}
	f.busy.Store(true)
	q := testQueue(f)
	ctx := context.Background()
	_, _ = q.Submit(ctx, "a1", "keep", "phone")
	_, _ = q.Submit(ctx, "a1", "from revoked", "old")
	q.DropDevice("old")
	if got := q.Pending("a1"); len(got) != 1 || got[0] != "keep" {
		t.Fatalf("after revoke %v", got)
	}
	q.Clear("a1")
	if got := q.Pending("a1"); len(got) != 0 {
		t.Fatalf("after clear %v", got)
	}
	for i := 0; i < maxQueuedPrompts; i++ {
		if _, err := q.Submit(ctx, "a2", "m", "phone"); err != nil {
			t.Fatalf("submit %d: %v", i, err)
		}
	}
	if _, err := q.Submit(ctx, "a2", "one too many", "phone"); err != errPromptQueueFull {
		t.Fatalf("cap err %v", err)
	}
	q.Clear("a2")
	_, _ = q.Submit(ctx, "a3", "late revoke", "revoked")
	f.busy.Store(false)
	time.Sleep(80 * time.Millisecond)
	for _, text := range f.delivered() {
		if text == "late revoke" || text == "from revoked" {
			t.Fatalf("delivered %q from an unauthorized device", text)
		}
	}
}
