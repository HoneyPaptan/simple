package api

import (
	"context"
	"encoding/base64"
	"encoding/json"
	"strconv"
	"sync"
	"sync/atomic"

	"github.com/coder/websocket"
	"strings"

	"sh.simple/simpled/internal/agents"
	"sh.simple/simpled/internal/binpath"
	"sh.simple/simpled/internal/term"
	"sh.simple/simpled/internal/wire"
)

const termChunk = 32 * 1024

var termSeq atomic.Int64

type termStream struct {
	mu       sync.Mutex
	sid      string
	attach   *term.Attach
	cols     int
	rows     int
	nextFull bool
}

func (s *termStream) markNextFull() {
	s.mu.Lock()
	s.nextFull = true
	s.mu.Unlock()
}

func (s *termStream) takeFull() bool {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.nextFull {
		s.nextFull = false
		return true
	}
	return false
}

func (s *termStream) setSize(cols, rows int) {
	s.mu.Lock()
	s.cols = cols
	s.rows = rows
	s.mu.Unlock()
}

func (s *termStream) size() (int, int) {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.cols, s.rows
}

type termState struct {
	mu     sync.Mutex
	stream *termStream
}

var startAttach = func(terminal string, cols, rows int) (*term.Attach, error) {
	bin, err := binpath.Find("hc")
	if err != nil {
		return nil, err
	}
	return term.Start(bin, terminal, cols, rows)
}

var startHerdrAttach = term.StartHerdr

var startHerdrTerminalAttach = term.StartHerdrTerminal

func (sess *session) termOpen(ctx context.Context, id *int64, p json.RawMessage) {
	var in wire.TermOpenParams
	if json.Unmarshal(p, &in) != nil || in.ID == "" {
		sess.writeErr(ctx, id, "bad_request", "missing id")
		return
	}
	var a *term.Attach
	var err error
	if agents.IsHerdrAgentID(in.ID) && sess.srv != nil && sess.srv.Herdr != nil {
		if sess.srv.Agents != nil {
			if target, ok := sess.srv.Agents.HerdrTarget(in.ID); ok {
				a, err = startHerdrAttach(target, in.Cols, in.Rows)
			}
		}
		if a == nil && err == nil {
			paneID := strings.TrimPrefix(in.ID, agents.HerdrIDPrefix())
			var termID string
			termID, err = sess.srv.Herdr.PaneTerminalID(ctx, paneID)
			if err == nil {
				a, err = startHerdrTerminalAttach(termID, in.Cols, in.Rows)
			}
		}
	} else if sess.srv != nil && sess.srv.Agents != nil && sess.srv.Agents.AgentRuntime(in.ID) == "herdr" {
		target, ok := sess.srv.Agents.HerdrTarget(in.ID)
		if !ok {
			sess.writeErr(ctx, id, "not_found", "")
			return
		}
		a, err = startHerdrAttach(target, in.Cols, in.Rows)
	} else {
		a, err = startAttach(in.ID, in.Cols, in.Rows)
	}
	if err != nil {
		sess.writeErr(ctx, id, "hc_unavailable", err.Error())
		return
	}
	stream := &termStream{sid: "s" + strconv.FormatInt(termSeq.Add(1), 10), attach: a}
	stream.setSize(in.Cols, in.Rows)
	stream.markNextFull()
	sess.term.mu.Lock()
	prev := sess.term.stream
	sess.term.stream = stream
	sess.term.mu.Unlock()
	if prev != nil {
		prev.attach.Close()
	}
	sess.writeResult(ctx, id, wire.TermOpenResult{Sid: stream.sid})
	pumpCtx := sess.ctx
	if pumpCtx == nil {
		pumpCtx = context.Background()
	}
	go sess.pumpTerm(pumpCtx, stream)
}

func (sess *session) pumpTerm(ctx context.Context, stream *termStream) {
	buf := make([]byte, termChunk)
	for {
		n, err := stream.attach.Read(buf)
		if n > 0 {
			full := stream.takeFull()
			if err := sess.writeFrame(ctx, wire.TermFrame{T: stream.sid, B: base64.StdEncoding.EncodeToString(buf[:n]), Full: full}); err != nil {
				break
			}
		}
		if err != nil {
			break
		}
	}
	stream.attach.Close()
	if sess.dropTerm(stream.sid) {
		_ = sess.writeFrame(ctx, wire.TermFrame{T: stream.sid, Done: true})
	}
}

func (sess *session) writeFrame(ctx context.Context, v any) error {
	frame, _ := json.Marshal(v)
	sess.mu.Lock()
	defer sess.mu.Unlock()
	if sess.captureWS != nil {
		sess.captureWS(frame)
		return nil
	}
	return sess.conn.Write(ctx, websocket.MessageText, frame)
}

func (sess *session) currentTerm(sid string) *termStream {
	sess.term.mu.Lock()
	defer sess.term.mu.Unlock()
	if sess.term.stream == nil || sess.term.stream.sid != sid {
		return nil
	}
	return sess.term.stream
}

func (sess *session) dropTerm(sid string) bool {
	sess.term.mu.Lock()
	defer sess.term.mu.Unlock()
	if sess.term.stream == nil || sess.term.stream.sid != sid {
		return false
	}
	sess.term.stream = nil
	return true
}

func (sess *session) termInput(sid, b64 string) {
	stream := sess.currentTerm(sid)
	if stream == nil {
		return
	}
	data, err := base64.StdEncoding.DecodeString(b64)
	if err != nil || len(data) == 0 {
		return
	}
	_, _ = stream.attach.Write(data)
}

func (sess *session) termResize(ctx context.Context, id *int64, p json.RawMessage) {
	var in wire.TermResizeParams
	if json.Unmarshal(p, &in) != nil || in.Sid == "" {
		sess.writeErr(ctx, id, "bad_request", "missing sid")
		return
	}
	stream := sess.currentTerm(in.Sid)
	if stream == nil {
		sess.writeErr(ctx, id, "not_found", "no terminal stream")
		return
	}
	if err := stream.attach.Resize(in.Cols, in.Rows); err != nil {
		sess.writeErr(ctx, id, "bad_request", err.Error())
		return
	}
	stream.setSize(in.Cols, in.Rows)
	stream.markNextFull()
	sess.writeResult(ctx, id, struct{}{})
}

func (sess *session) termSnap(ctx context.Context, id *int64, p json.RawMessage) {
	var in wire.TermSnapParams
	if json.Unmarshal(p, &in) != nil || in.Sid == "" {
		sess.writeErr(ctx, id, "bad_request", "missing sid")
		return
	}
	stream := sess.currentTerm(in.Sid)
	if stream == nil {
		sess.writeErr(ctx, id, "not_found", "no terminal stream")
		return
	}
	cols, rows := stream.size()
	if cols < 2 || rows < 2 {
		sess.writeErr(ctx, id, "bad_request", "no size")
		return
	}
	if err := stream.attach.Resize(cols, rows); err != nil {
		sess.writeErr(ctx, id, "bad_request", err.Error())
		return
	}
	stream.markNextFull()
	sess.writeResult(ctx, id, struct{}{})
}

func (sess *session) termClose(ctx context.Context, id *int64, p json.RawMessage) {
	var in wire.TermCloseParams
	_ = json.Unmarshal(p, &in)
	if stream := sess.currentTerm(in.Sid); stream != nil && sess.dropTerm(in.Sid) {
		stream.attach.Close()
	}
	sess.writeResult(ctx, id, struct{}{})
}

func (sess *session) closeTerm() {
	sess.term.mu.Lock()
	stream := sess.term.stream
	sess.term.stream = nil
	sess.term.mu.Unlock()
	if stream == nil {
		return
	}
	stream.attach.Close()
	ctx := sess.ctx
	if ctx == nil {
		ctx = context.Background()
	}
	_ = sess.writeFrame(ctx, wire.TermFrame{T: stream.sid, Done: true})
}
