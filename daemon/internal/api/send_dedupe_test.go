package api

import (
	"testing"
	"time"
)

func TestSendDedupeClaimsOnceUntilReleasedOrExpired(t *testing.T) {
	var d sendDedupe
	now := time.Unix(1000, 0)
	if !d.claim("phone:c1", now) {
		t.Fatal("first claim refused")
	}
	if d.claim("phone:c1", now.Add(time.Second)) {
		t.Fatal("retry of the same send was accepted")
	}
	d.release("phone:c1")
	if !d.claim("phone:c1", now.Add(2*time.Second)) {
		t.Fatal("claim after a failed send refused")
	}
	if !d.claim("phone:c1", now.Add(sendDedupeTTL+3*time.Second)) {
		t.Fatal("claim after expiry refused")
	}
}

func TestSendClientIDReadsCid(t *testing.T) {
	if got := sendClientID([]byte(`{"id":"t1","text":"hi","cid":"abc"}`)); got != "abc" {
		t.Fatalf("cid %q", got)
	}
	if got := sendClientID([]byte(`{"id":"t1","text":"hi"}`)); got != "" {
		t.Fatalf("cid %q", got)
	}
}
