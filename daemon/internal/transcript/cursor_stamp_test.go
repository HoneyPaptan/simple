package transcript

import (
	"testing"
	"time"

	"sh.simple/simpled/internal/wire"
)

func TestCursorStampKeepsFirstLoadAndTimesNewLines(t *testing.T) {
	m := NewManager(func(string, any) {})
	first := []wire.Message{{ID: "a", Ts: 1000}, {ID: "b", Ts: 1001}}
	m.stampCursor("/p", first, time.UnixMilli(5000))
	if first[0].Ts != 1000 || first[1].Ts != 1001 {
		t.Fatalf("first load changed: %+v", first)
	}
	later := []wire.Message{{ID: "a", Ts: 1000}, {ID: "b", Ts: 1001}, {ID: "c", Ts: 1002}, {ID: "d", Ts: 1003}}
	m.stampCursor("/p", later, time.UnixMilli(9000))
	if later[2].Ts != 9000 || later[3].Ts != 9001 {
		t.Fatalf("new lines not stamped: %+v", later)
	}
	again := []wire.Message{{ID: "c", Ts: 1002}}
	m.stampCursor("/p", again, time.UnixMilli(20000))
	if again[0].Ts != 9000 {
		t.Fatalf("stamp not stable: %+v", again)
	}
}

func TestChatSinceIsEarliestTimedMessage(t *testing.T) {
	msgs := []wire.Message{{ID: "a", Ts: 0}, {ID: "b", Ts: 700}, {ID: "c", Ts: 500}}
	if got := chatSince(msgs); got != 500 {
		t.Fatalf("since %d", got)
	}
	if got := chatSince(nil); got != 0 {
		t.Fatalf("empty since %d", got)
	}
}
