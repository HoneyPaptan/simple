package api

import "testing"

func TestTermStreamFullMarker(t *testing.T) {
	stream := &termStream{}
	if stream.takeFull() {
		t.Fatal("unset full")
	}
	stream.markNextFull()
	if !stream.takeFull() {
		t.Fatal("expected full")
	}
	if stream.takeFull() {
		t.Fatal("full should be one shot")
	}
}
