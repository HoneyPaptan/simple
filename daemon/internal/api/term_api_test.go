package api

import (
	"context"
	"encoding/base64"
	"encoding/json"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"testing"
	"time"

	"sh.simple/simpled/internal/term"
	"sh.simple/simpled/internal/wire"
)

type frameLog struct {
	mu     sync.Mutex
	frames [][]byte
}

func (l *frameLog) add(b []byte) {
	l.mu.Lock()
	l.frames = append(l.frames, append([]byte(nil), b...))
	l.mu.Unlock()
}

func (l *frameLog) termText() (string, bool) {
	l.mu.Lock()
	defer l.mu.Unlock()
	var out strings.Builder
	done := false
	for _, b := range l.frames {
		var f wire.TermFrame
		if json.Unmarshal(b, &f) != nil || f.T == "" {
			continue
		}
		raw, _ := base64.StdEncoding.DecodeString(f.B)
		out.Write(raw)
		done = done || f.Done
	}
	return out.String(), done
}

func useFakeAttach(t *testing.T) {
	t.Helper()
	bin := filepath.Join(t.TempDir(), "hc")
	script := "#!/bin/sh\necho ready\nread line\necho \"got $line\"\n"
	if err := os.WriteFile(bin, []byte(script), 0o755); err != nil {
		t.Fatal(err)
	}
	prev := startAttach
	startAttach = func(terminal string, cols, rows int) (*term.Attach, error) {
		return term.Start(bin, terminal, cols, rows)
	}
	t.Cleanup(func() { startAttach = prev })
}

func waitTerm(t *testing.T, log *frameLog, want string, wantDone bool) {
	t.Helper()
	deadline := time.Now().Add(5 * time.Second)
	for time.Now().Before(deadline) {
		text, done := log.termText()
		if strings.Contains(text, want) && (!wantDone || done) {
			return
		}
		time.Sleep(20 * time.Millisecond)
	}
	text, done := log.termText()
	t.Fatalf("want %q done=%v, got %q done=%v", want, wantDone, text, done)
}

func TestTermStreamsBytesBothWaysAndEnds(t *testing.T) {
	useFakeAttach(t)
	log := &frameLog{}
	sess := &session{helloOK: true, captureWS: log.add}
	id := int64(1)
	sess.termOpen(context.Background(), &id, json.RawMessage(`{"id":"t1","cols":80,"rows":24}`))
	var res struct {
		R wire.TermOpenResult `json:"r"`
	}
	if err := json.Unmarshal(log.frames[0], &res); err != nil || res.R.Sid == "" {
		t.Fatalf("open result %s", log.frames[0])
	}
	waitTerm(t, log, "ready", false)
	sess.termInput(res.R.Sid, base64.StdEncoding.EncodeToString([]byte("hi\r")))
	waitTerm(t, log, "got hi", true)
	if sess.currentTerm(res.R.Sid) != nil {
		t.Fatal("ended stream still current")
	}
}
