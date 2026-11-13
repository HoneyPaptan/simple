package api

import (
	"context"
	"encoding/json"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"sh.simple/simpled/internal/agents"
	"sh.simple/simpled/internal/herdr"
	"sh.simple/simpled/internal/term"
)

func writeExec(t *testing.T, dir, name, body string) string {
	t.Helper()
	path := filepath.Join(dir, name)
	if err := os.WriteFile(path, []byte(body), 0o755); err != nil {
		t.Fatal(err)
	}
	return path
}

func fakeHCScreen(t *testing.T, screen string) string {
	dir := t.TempDir()
	script := "#!/bin/sh\necho " + screen + "\nexec sleep 3600\n"
	return writeExec(t, dir, "hc", script)
}

func fakeHerdrBusyThenScreen(t *testing.T) string {
	dir := t.TempDir()
	flag := filepath.Join(dir, "busy_once")
	script := "#!/bin/sh\n" +
		"case \"$*\" in\n" +
		"  *attach*)\n" +
		"    if [ -f \"" + flag + "\" ]; then rm -f \"" + flag + "\"; printf \"login\\n\"; else touch \"" + flag + "\"; printf \"read in progress\\n\"; fi\n" +
		"    while read line; do :; done ;;\n" +
		"esac\n"
	return writeExec(t, dir, "herdr", script)
}

func herdrHarnessSession(t *testing.T) *session {
	t.Helper()
	store := agents.NewStore(func(string, any) {})
	store.LoadHerdrAgents([]herdr.AgentInfo{{
		Agent:  "claude",
		PaneID: "p1",
		Name:   "claude",
		Cwd:    t.TempDir(),
	}})
	return &session{helloOK: true, srv: &Server{Agents: store}}
}

func openTerm(t *testing.T, sess *session, log *frameLog, agentID string) string {
	t.Helper()
	sess.captureWS = log.add
	id := int64(1)
	before := len(log.frames)
	sess.termOpen(context.Background(), &id, json.RawMessage(`{"id":"`+agentID+`","cols":80,"rows":24}`))
	var sid string
	for _, frame := range log.frames[before:] {
		var res struct {
			R struct {
				Sid string `json:"sid"`
			} `json:"r"`
		}
		if json.Unmarshal(frame, &res) == nil && res.R.Sid != "" {
			sid = res.R.Sid
			break
		}
	}
	if sid == "" {
		t.Fatalf("open missing sid after %d frames", len(log.frames)-before)
	}
	return sid
}

func framesText(log *frameLog) string {
	text, _ := log.termText()
	return text
}

func waitFrames(t *testing.T, log *frameLog, want string) {
	t.Helper()
	deadline := time.Now().Add(3 * time.Second)
	for time.Now().Before(deadline) {
		if strings.Contains(framesText(log), want) {
			return
		}
		time.Sleep(15 * time.Millisecond)
	}
	t.Fatalf("want %q got %q", want, framesText(log))
}

func firstFrameFull(t *testing.T, log *frameLog) bool {
	t.Helper()
	log.mu.Lock()
	defer log.mu.Unlock()
	for _, b := range log.frames {
		var f struct {
			T    string `json:"t"`
			Full bool   `json:"full"`
		}
		if json.Unmarshal(b, &f) != nil || f.T == "" {
			continue
		}
		return f.Full
	}
	return false
}

func TestTermAttachHarnessHCQuickReattach(t *testing.T) {
	hc := fakeHCScreen(t, "screen-a")
	prevAttach := startAttach
	startAttach = func(terminal string, cols, rows int) (*term.Attach, error) {
		return term.Start(hc, terminal, cols, rows)
	}
	t.Cleanup(func() { startAttach = prevAttach })

	log := &frameLog{}
	sess := &session{helloOK: true, captureWS: log.add}
	s1 := openTerm(t, sess, log, "t1")
	waitFrames(t, log, "screen-a")
	if !firstFrameFull(t, log) {
		t.Fatal("first hc frame not full")
	}
	sess.termClose(context.Background(), int64Ptr(2), json.RawMessage(`{"sid":"`+s1+`"}`))
	s2 := openTerm(t, sess, log, "t1")
	if s1 == s2 {
		t.Fatal("reattach should use a new sid")
	}
	waitFrames(t, log, "screen-a")
	if sess.currentTerm(s1) != nil {
		t.Fatal("stale sid still current")
	}
}

func TestTermAttachHarnessLinkDropEndsStream(t *testing.T) {
	hc := fakeHCScreen(t, "live")
	prevAttach := startAttach
	startAttach = func(terminal string, cols, rows int) (*term.Attach, error) {
		return term.Start(hc, terminal, cols, rows)
	}
	t.Cleanup(func() { startAttach = prevAttach })

	log := &frameLog{}
	sess := &session{helloOK: true, captureWS: log.add}
	sid := openTerm(t, sess, log, "t9")
	waitFrames(t, log, "live")
	sess.closeTerm()
	waitTerm(t, log, "live", true)
	if sess.currentTerm(sid) != nil {
		t.Fatal("stream still registered after link drop")
	}
}

func TestTermAttachHarnessHerdrBusyRetry(t *testing.T) {
	herdr := fakeHerdrBusyThenScreen(t)
	prev := startHerdrAttach
	startHerdrAttach = func(target string, cols, rows int) (*term.Attach, error) {
		term.HerdrBin = herdr
		return term.StartHerdr(target, cols, rows)
	}
	t.Cleanup(func() {
		startHerdrAttach = prev
		term.HerdrBin = ""
	})

	log := &frameLog{}
	sess := herdrHarnessSession(t)
	sess.captureWS = log.add
	sid := openTerm(t, sess, log, "h:p1")
	waitFrames(t, log, "login")
	if !firstFrameFull(t, log) {
		t.Fatal("first herdr frame not full")
	}
	if sess.currentTerm(sid) == nil {
		t.Fatal("herdr attach missing")
	}
}

func TestTermAttachHarnessHerdrRestartChangesScreen(t *testing.T) {
	dir := t.TempDir()
	gen := filepath.Join(dir, "gen")
	script := "#!/bin/sh\n" +
		"case \"$*\" in\n" +
		"  *attach*)\n" +
		"    n=$(cat \"" + gen + "\" 2>/dev/null || echo 0)\n" +
		"    echo $((n+1)) > \"" + gen + "\"\n" +
		"    if [ \"$n\" = \"0\" ]; then printf v1; else printf v2; fi\n" +
		"    while read line; do :; done ;;\n" +
		"esac\n"
	herdr := writeExec(t, dir, "herdr", script)
	prev := startHerdrAttach
	startHerdrAttach = func(target string, cols, rows int) (*term.Attach, error) {
		term.HerdrBin = herdr
		return term.StartHerdr(target, cols, rows)
	}
	t.Cleanup(func() {
		startHerdrAttach = prev
		term.HerdrBin = ""
	})

	log := &frameLog{}
	sess := herdrHarnessSession(t)
	sess.captureWS = log.add
	s1 := openTerm(t, sess, log, "h:p1")
	waitFrames(t, log, "v1")
	sess.termClose(context.Background(), int64Ptr(3), json.RawMessage(`{"sid":"`+s1+`"}`))
	s2 := openTerm(t, sess, log, "h:p1")
	waitFrames(t, log, "v2")
	if s1 == s2 {
		t.Fatal("expected new sid after restart attach")
	}
}

func TestTermAttachHarnessSecondOpenReplacesFirst(t *testing.T) {
	hc := fakeHCScreen(t, "ok")
	prevAttach := startAttach
	startAttach = func(terminal string, cols, rows int) (*term.Attach, error) {
		return term.Start(hc, terminal, cols, rows)
	}
	t.Cleanup(func() { startAttach = prevAttach })

	log := &frameLog{}
	sess := &session{helloOK: true, captureWS: log.add}
	a := openTerm(t, sess, log, "t1")
	b := openTerm(t, sess, log, "t2")
	if a == b {
		t.Fatal("idempotent open expected new sid")
	}
	waitFrames(t, log, "ok")
	if sess.currentTerm(a) != nil {
		t.Fatal("first stream should be replaced")
	}
}

func int64Ptr(v int64) *int64 { return &v }
