package hooks

import (
	"context"
	"testing"
	"time"
)

func TestVerdictOnlyAllowsKnownDecisions(t *testing.T) {
	cases := map[string][2]bool{
		"allow":  {true, true},
		"always": {true, true},
		"deny":   {false, true},
		"":       {false, false},
		"skip":   {false, false},
	}
	for decision, want := range cases {
		allow, known := (&inboxAnswer{decision: decision}).verdict()
		if allow != want[0] || known != want[1] {
			t.Fatalf("%q: got %v %v", decision, allow, known)
		}
	}
	if allow, known := (*inboxAnswer)(nil).verdict(); allow || known {
		t.Fatal("nil answer must be unknown")
	}
}

func TestDeliverAnswerReachesEveryWaiter(t *testing.T) {
	d := &Daemon{pending: map[string][]chan inboxAnswer{}}
	got := make(chan string, 2)
	for i := 0; i < 2; i++ {
		go func() {
			if ans := d.waitAnswer(context.Background(), "item", time.Minute); ans != nil {
				got <- ans.decision
			}
		}()
	}
	deadline := time.Now().Add(2 * time.Second)
	for {
		d.mu.Lock()
		n := len(d.pending["item"])
		d.mu.Unlock()
		if n == 2 || time.Now().After(deadline) {
			break
		}
		time.Sleep(5 * time.Millisecond)
	}
	d.DeliverAnswer("item", "deny", nil, nil)
	d.DeliverAnswer("item", "allow", nil, nil)
	for i := 0; i < 2; i++ {
		select {
		case decision := <-got:
			if decision != "deny" {
				t.Fatalf("waiter got %q", decision)
			}
		case <-time.After(2 * time.Second):
			t.Fatal("waiter never answered")
		}
	}
}
