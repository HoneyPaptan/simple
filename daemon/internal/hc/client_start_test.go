package hc

import (
	"context"
	"testing"
	"time"
)

func TestTryStartRunsStarterAtMostOncePerMinute(t *testing.T) {
	c := NewClient("/nonexistent")
	calls := 0
	c.SetStarter(func(context.Context) error { calls++; return nil })
	now := time.Now()
	c.tryStart(context.Background(), now)
	c.tryStart(context.Background(), now.Add(30*time.Second))
	c.tryStart(context.Background(), now.Add(61*time.Second))
	if calls != 2 {
		t.Fatalf("starter ran %d times, want 2", calls)
	}
}
