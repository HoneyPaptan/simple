package api

import (
	"context"
	"encoding/json"
	"testing"
)

func TestTermSnapAcceptsOpenStream(t *testing.T) {
	useFakeAttach(t)
	log := &frameLog{}
	sess := &session{helloOK: true, captureWS: log.add}
	id := int64(1)
	sess.termOpen(context.Background(), &id, json.RawMessage(`{"id":"t1","cols":80,"rows":24}`))
	var res struct {
		R struct {
			Sid string `json:"sid"`
		} `json:"r"`
	}
	if err := json.Unmarshal(log.frames[0], &res); err != nil || res.R.Sid == "" {
		t.Fatalf("open: %s", log.frames[0])
	}
	waitTerm(t, log, "ready", false)
	sess.termSnap(context.Background(), int64Ptr(2), json.RawMessage(`{"sid":"`+res.R.Sid+`"}`))
	stream := sess.currentTerm(res.R.Sid)
	if stream == nil {
		t.Fatal("stream missing after snap")
	}
	if !stream.takeFull() {
		t.Fatal("snap should arm the next full frame")
	}
}

func TestTermResizeArmsFullFrame(t *testing.T) {
	useFakeAttach(t)
	log := &frameLog{}
	sess := &session{helloOK: true, captureWS: log.add}
	id := int64(1)
	sess.termOpen(context.Background(), &id, json.RawMessage(`{"id":"t1","cols":80,"rows":24}`))
	var res struct {
		R struct {
			Sid string `json:"sid"`
		} `json:"r"`
	}
	if err := json.Unmarshal(log.frames[0], &res); err != nil || res.R.Sid == "" {
		t.Fatalf("open: %s", log.frames[0])
	}
	waitTerm(t, log, "ready", false)
	stream := sess.currentTerm(res.R.Sid)
	stream.takeFull()
	sess.termResize(context.Background(), int64Ptr(3), json.RawMessage(`{"sid":"`+res.R.Sid+`","cols":80,"rows":20}`))
	if !stream.takeFull() {
		t.Fatal("resize should arm the next full frame")
	}
}
