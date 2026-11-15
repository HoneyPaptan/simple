package api

import (
	"context"
	"crypto/tls"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log"
	"net"
	"net/http"
	"sh.simple/simpled/internal/opencode"
	"strings"
	"sync"
	"time"

	"github.com/coder/websocket"

	"sh.simple/simpled/internal/agents"
	"sh.simple/simpled/internal/commands"
	"sh.simple/simpled/internal/config"
	"sh.simple/simpled/internal/hc"
	"sh.simple/simpled/internal/herdr"
	"sh.simple/simpled/internal/hooks"
	"sh.simple/simpled/internal/hub"
	"sh.simple/simpled/internal/inbox"
	"sh.simple/simpled/internal/pair"
	"sh.simple/simpled/internal/servers"
	"sh.simple/simpled/internal/transcript"
	"sh.simple/simpled/internal/usage"
	"sh.simple/simpled/internal/webhooks"
	"sh.simple/simpled/internal/wire"
)

type Server struct {
	Store       *config.Store
	Hub         *hub.Hub
	Agents      *agents.Store
	HC          *hc.Client
	Herdr       *herdr.Client
	Pair        *pair.Manager
	Inbox       *inbox.Store
	Hooks       *hooks.Daemon
	Transcripts *transcript.Manager
	Usage       *usage.Summarizer
	Preview     *servers.PreviewStore
	Webhooks    *webhooks.Dispatcher
	ListenPort  int
	fontCache   []wire.FontEntry
	Screens     *ScreenWatcher
	SimpledPath string
	HostID      string
	Linger      bool
	OpenCode    *opencode.Client
	Approvals   *opencode.Approvals
	Commands    *commands.Service

	sends       sendDedupe
	prompts     *promptQueue
	promptMu    sync.Mutex
	sendWatchMu sync.Mutex
	sendWatch   map[string]context.CancelFunc
	mu          sync.Mutex
	sessions    map[*session]struct{}
	byDevice    map[string][]*session

	servers []*http.Server
}

type Options struct {
	Store       *config.Store
	Hub         *hub.Hub
	Agents      *agents.Store
	HC          *hc.Client
	Herdr       *herdr.Client
	Pair        *pair.Manager
	Inbox       *inbox.Store
	Hooks       *hooks.Daemon
	Transcripts *transcript.Manager
	Usage       *usage.Summarizer
	Preview     *servers.PreviewStore
	Webhooks    *webhooks.Dispatcher
	ListenPort  int
	Screens     *ScreenWatcher
	SimpledPath string
	HostID      string
	Linger      bool
	OpenCode    *opencode.Client
	Approvals   *opencode.Approvals
}

func New(opts Options) *Server {
	return &Server{
		Store:       opts.Store,
		Hub:         opts.Hub,
		Agents:      opts.Agents,
		HC:          opts.HC,
		Herdr:       opts.Herdr,
		Pair:        opts.Pair,
		Inbox:       opts.Inbox,
		Hooks:       opts.Hooks,
		Transcripts: opts.Transcripts,
		Usage:       optsUsage(opts.Usage),
		Preview:     optsPreview(opts.Preview),
		Webhooks:    opts.Webhooks,
		ListenPort:  opts.ListenPort,
		Screens:     opts.Screens,
		SimpledPath: opts.SimpledPath,
		HostID:      opts.HostID,
		Linger:      opts.Linger,
		OpenCode:    opts.OpenCode,
		Approvals:   opts.Approvals,
		Commands:    commands.NewService(opts.OpenCode),
		sessions:    make(map[*session]struct{}),
		byDevice:    make(map[string][]*session),
	}
}

func (s *Server) Start(ctx context.Context, cfg config.Config) error {
	tlsCfg, _, err := s.Store.TLSConfig()
	if err != nil {
		return err
	}
	addrs, err := listenAddrs(cfg.Port, cfg.Lan)
	if err != nil {
		return err
	}
	mux := http.NewServeMux()
	mux.HandleFunc("POST /v1/pair", s.handlePair)
	mux.HandleFunc("GET /v1/ws", s.handleWS)
	mux.HandleFunc("/v1/preview/", s.handlePreview)
	mux.Handle("/", s.Preview.RootHandler())
	mux.HandleFunc("/v1/file", s.handleFile)
	go s.fanout(ctx)
	for _, addr := range addrs {
		ln, err := net.Listen("tcp", addr)
		if err != nil {
			log.Printf("listen %s: %v", addr, err)
			continue
		}
		tlsLn := tls.NewListener(ln, tlsCfg)
		srv := &http.Server{Handler: mux}
		s.mu.Lock()
		s.servers = append(s.servers, srv)
		s.mu.Unlock()
		go func(a string) {
			log.Printf("https %s", a)
			if err := srv.Serve(tlsLn); err != nil && !errors.Is(err, http.ErrServerClosed) {
				log.Printf("serve %s: %v", a, err)
			}
		}(addr)
	}
	return nil
}

func (s *Server) Shutdown(ctx context.Context) {
	s.mu.Lock()
	servers := s.servers
	s.mu.Unlock()
	for _, srv := range servers {
		srv.Shutdown(ctx)
	}
}

func (s *Server) fanout(ctx context.Context) {
	last := s.Hub.Seq()
	for ctx.Err() == nil {
		last = s.fanoutOnce(ctx, last)
	}
}

func (s *Server) fanoutOnce(ctx context.Context, last int64) int64 {
	ch, unsub := s.Hub.Subscribe()
	defer unsub()
	missed, ok := s.Hub.Since(last)
	if !ok {
		s.dropAllSessions()
		missed = nil
		last = s.Hub.Seq()
	}
	for _, ev := range missed {
		s.broadcastEvent(ev)
		last = ev.Seq
	}
	for {
		select {
		case <-ctx.Done():
			return last
		case ev, ok := <-ch:
			if !ok {
				return last
			}
			if ev.Seq <= last {
				continue
			}
			s.broadcastEvent(ev)
			last = ev.Seq
		}
	}
}

func (s *Server) broadcastEvent(ev hub.Event) {
	frame, _ := json.Marshal(wire.WSEvent{Ev: ev.Ev, Seq: ev.Seq, D: ev.D})
	s.mu.Lock()
	defer s.mu.Unlock()
	for sess := range s.sessions {
		select {
		case sess.send <- frame:
		default:
			delete(s.sessions, sess)
			go sess.close()
		}
	}
}

func (s *Server) dropAllSessions() {
	s.mu.Lock()
	defer s.mu.Unlock()
	for sess := range s.sessions {
		delete(s.sessions, sess)
		go sess.close()
	}
}

func (s *Server) registerSession(sess *session) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.sessions[sess] = struct{}{}
	s.byDevice[sess.deviceID] = append(s.byDevice[sess.deviceID], sess)
}

func (s *Server) unregisterSession(sess *session) {
	s.mu.Lock()
	defer s.mu.Unlock()
	delete(s.sessions, sess)
	list := s.byDevice[sess.deviceID]
	out := list[:0]
	for _, x := range list {
		if x != sess {
			out = append(out, x)
		}
	}
	if len(out) == 0 {
		delete(s.byDevice, sess.deviceID)
	} else {
		s.byDevice[sess.deviceID] = out
	}
}

func (s *Server) closeDeviceSessions(deviceID string) {
	s.mu.Lock()
	list := append([]*session(nil), s.byDevice[deviceID]...)
	s.mu.Unlock()
	for _, sess := range list {
		sess.close()
	}
}

func (s *Server) handlePair(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "method", http.StatusMethodNotAllowed)
		return
	}
	var body wire.PairRequest
	if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
		w.WriteHeader(http.StatusBadRequest)
		json.NewEncoder(w).Encode(wire.PairError{Kind: "bad_request"})
		return
	}
	ok, kind := s.Pair.Validate(body.Code)
	if !ok {
		if kind == "used" {
			w.WriteHeader(http.StatusConflict)
		} else {
			w.WriteHeader(http.StatusUnauthorized)
		}
		json.NewEncoder(w).Encode(wire.PairError{Kind: kind})
		return
	}
	name := body.Device.Name
	model := body.Device.Model
	var revokeID string
	if body.ReplaceToken != nil {
		repl := strings.TrimSpace(*body.ReplaceToken)
		if repl != "" {
			if old, _ := s.Store.FindDeviceByTokenSHA256(repl); old != nil {
				name = old.Name
				revokeID = old.ID
			}
		}
	}
	token, err := config.NewToken()
	if err != nil {
		w.WriteHeader(http.StatusInternalServerError)
		return
	}
	dev, err := s.Store.AddDevice(name, model, token)
	if err != nil {
		w.WriteHeader(http.StatusInternalServerError)
		return
	}
	if revokeID != "" {
		_ = s.Store.RevokeDevice(revokeID)
		s.closeDeviceSessions(revokeID)
	}
	hcVer := s.HC.Version()
	resp := wire.PairResponse{
		DeviceID: dev.ID,
		Token:    token,
		Host: wire.HostInfo{
			ID:      s.HostID,
			Name:    s.Store.HostName(),
			Version: config.Version,
			HC:      hcVer,
			Herdr:   s.herdrVersion(),
			Linger:  s.Linger,
		},
	}
	w.WriteHeader(http.StatusOK)
	json.NewEncoder(w).Encode(resp)
}

func (s *Server) handleWS(w http.ResponseWriter, r *http.Request) {
	token := bearerToken(r)
	if token == "" {
		http.Error(w, "unauthorized", http.StatusUnauthorized)
		return
	}
	dev, err := s.Store.FindDeviceByToken(token)
	if err != nil || dev == nil {
		http.Error(w, "unauthorized", http.StatusUnauthorized)
		return
	}
	_ = s.Store.TouchDevice(dev.ID)
	c, err := websocket.Accept(w, r, nil)
	if err != nil {
		return
	}
	sess := newSession(s, c, dev.ID)
	s.registerSession(sess)
	defer func() {
		s.unregisterSession(sess)
		sess.close()
	}()
	sess.run(r.Context())
}

func bearerToken(r *http.Request) string {
	h := r.Header.Get("Authorization")
	if len(h) > 7 && h[:7] == "Bearer " {
		return h[7:]
	}
	return ""
}

type session struct {
	srv       *Server
	conn      *websocket.Conn
	deviceID  string
	send      chan []byte
	helloOK   bool
	closed    bool
	captureWS func([]byte)
	watched   map[string]bool
	mu        sync.Mutex
	ctx       context.Context
	term      termState
}

func newSession(srv *Server, c *websocket.Conn, deviceID string) *session {
	return &session{
		srv:      srv,
		conn:     c,
		deviceID: deviceID,
		send:     make(chan []byte, 64),
	}
}

func (sess *session) close() {
	sess.mu.Lock()
	defer sess.mu.Unlock()
	if sess.closed {
		return
	}
	sess.closed = true
	for id := range sess.watched {
		sess.srv.Transcripts.SetWatch(id, false)
	}
	sess.watched = nil
	sess.closeTerm()
	if sess.srv.Screens != nil {
		sess.srv.Screens.StopAll(sess)
	}
	sess.conn.Close(websocket.StatusNormalClosure, "")
}

const (
	pingEvery   = 15 * time.Second
	pongTimeout = 30 * time.Second
)

func (sess *session) keepAlive(ctx context.Context, cancel context.CancelFunc, every, timeout time.Duration) {
	tick := time.NewTicker(every)
	defer tick.Stop()
	for {
		select {
		case <-ctx.Done():
			return
		case <-tick.C:
			pctx, done := context.WithTimeout(ctx, timeout)
			err := sess.conn.Ping(pctx)
			done()
			if err != nil {
				_ = sess.conn.CloseNow()
				sess.close()
				cancel()
				return
			}
		}
	}
}

func (sess *session) run(ctx context.Context) {
	ctx, cancel := context.WithCancel(ctx)
	defer cancel()
	defer sess.closeTerm()
	sess.ctx = ctx
	go sess.keepAlive(ctx, cancel, pingEvery, pongTimeout)
	go func() {
		for {
			select {
			case <-ctx.Done():
				return
			case msg, ok := <-sess.send:
				if !ok {
					return
				}
				sess.mu.Lock()
				err := sess.conn.Write(ctx, websocket.MessageText, msg)
				sess.mu.Unlock()
				if err != nil {
					cancel()
					return
				}
			}
		}
	}()
	for {
		typ, data, err := sess.conn.Read(ctx)
		if err != nil {
			return
		}
		if typ != websocket.MessageText {
			continue
		}
		var req struct {
			ID *int64          `json:"id"`
			M  string          `json:"m"`
			P  json.RawMessage `json:"p"`
			T  string          `json:"t"`
			B  string          `json:"b"`
		}
		if json.Unmarshal(data, &req) != nil {
			continue
		}
		if req.M == "hello" {
			sess.handleHello(ctx, req.ID, req.P)
			continue
		}
		if !sess.helloOK {
			sess.writeErr(ctx, req.ID, "bad_request", "hello first")
			continue
		}
		if req.T != "" {
			sess.termInput(req.T, req.B)
			continue
		}
		if req.ID == nil {
			continue
		}
		sess.dispatch(ctx, *req.ID, req.M, req.P)
	}
}

func (sess *session) handleHello(ctx context.Context, id *int64, p json.RawMessage) {
	var hp wire.HelloParams
	_ = json.Unmarshal(p, &hp)
	agentsSnap := sess.srv.Agents.Snapshot()
	workspaces := sess.srv.Agents.Workspaces()
	hcVer := sess.srv.HC.Version()
	var herdrVer *string
	if sess.srv.Herdr != nil {
		herdrVer = sess.srv.Herdr.Version(ctx)
	}
	replay := false
	var snapshot *wire.Snapshot
	var replayEvents []hub.Event
	if hp.Epoch != "" && hp.Epoch == sess.srv.Hub.Epoch() && hp.Since != nil {
		evs, ok := sess.srv.Hub.Since(*hp.Since)
		if ok {
			replay = true
			replayEvents = evs
		}
	}
	if !replay {
		inboxItems := []wire.Item{}
		if sess.srv.Inbox != nil {
			inboxItems = sess.srv.Inbox.Snapshot()
		}
		snapshot = &wire.Snapshot{
			Agents:     agentsSnap,
			Inbox:      inboxItems,
			Workspaces: workspaces,
		}
	}
	res := wire.HelloResult{
		Host: wire.HostInfo{
			ID:      sess.srv.HostID,
			Name:    sess.srv.Store.HostName(),
			Version: config.Version,
			Epoch:   sess.srv.Hub.Epoch(),
			HC:      hcVer,
			Herdr:   herdrVer,
			Linger:  sess.srv.Linger,
		},
		Seq:      sess.srv.Hub.Seq(),
		Replay:   replay,
		Snapshot: snapshot,
	}
	sess.helloOK = true
	sess.writeResult(ctx, id, res)
	for _, ev := range replayEvents {
		frame, _ := json.Marshal(wire.WSEvent{Ev: ev.Ev, Seq: ev.Seq, D: ev.D})
		select {
		case sess.send <- frame:
		default:
		}
	}
}

func (sess *session) dispatch(ctx context.Context, id int64, method string, p json.RawMessage) {
	switch method {
	case "agents.list":
		list := sess.srv.Agents.Snapshot()
		sess.writeResult(ctx, &id, wire.AgentsListResult{Agents: list})
	case "workspaces.list":
		ws := sess.srv.Agents.Workspaces()
		sess.writeResult(ctx, &id, wire.WorkspacesListResult{Workspaces: ws})
	case "devices.list":
		devs, err := sess.srv.Store.LoadDevices()
		if err != nil {
			sess.writeErr(ctx, &id, "internal", err.Error())
			return
		}
		views := make([]wire.DeviceView, len(devs))
		for i, d := range devs {
			views[i] = wire.DeviceView{ID: d.ID, Name: d.Name, Model: d.Model, Created: d.Created, LastSeen: d.LastSeen}
		}
		sess.writeResult(ctx, &id, wire.DevicesListResult{Devices: views})
	case "devices.revoke":
		var par struct {
			ID string `json:"id"`
		}
		if json.Unmarshal(p, &par) != nil || par.ID == "" {
			sess.writeErr(ctx, &id, "bad_request", "")
			return
		}
		if err := sess.srv.Store.RevokeDevice(par.ID); err != nil {
			sess.writeErr(ctx, &id, "not_found", "")
			return
		}
		sess.srv.promptQueue().DropDevice(par.ID)
		sess.srv.closeDeviceSessions(par.ID)
		sess.writeResult(ctx, &id, map[string]any{})
	case "agent.send":
		var fresh bool
		if ctx, fresh = sess.claimSend(ctx, p); !fresh {
			sess.writeResult(ctx, &id, struct{}{})
			return
		}
		if sess.sendOpenCode(ctx, &id, p) {
			return
		}
		if sess.queueSend(ctx, &id, p) {
			return
		}
		if sess.agentHerdr(ctx, &id, "send", p) {
			return
		}
		sess.agentHC(ctx, &id, "terminal.send", p, mapSendParams)
	case "agent.keys":
		if sess.agentHerdr(ctx, &id, "keys", p) {
			return
		}
		sess.agentHC(ctx, &id, "terminal.keys", p, mapKeysParams)
	case "agent.interrupt":
		sess.clearQueuedPrompts(p)
		sess.markStopped(p)
		if sess.interruptOpenCode(ctx, &id, p) {
			return
		}
		if sess.agentHerdr(ctx, &id, "interrupt", p) {
			return
		}
		sess.agentHC(ctx, &id, "terminal.interrupt", p, mapTermOnly)
	case "agent.kill":
		sess.clearQueuedPrompts(p)
		if sess.agentHerdr(ctx, &id, "kill", p) {
			return
		}
		sess.agentHC(ctx, &id, "terminal.kill", p, mapTermOnly)
	case "agent.remove":
		sess.clearQueuedPrompts(p)
		if sess.agentHerdr(ctx, &id, "remove", p) {
			return
		}
		sess.agentHC(ctx, &id, "terminal.rm", p, mapTermOnly)
	case "agent.screen":
		if sess.agentHerdr(ctx, &id, "screen", p) {
			return
		}
		sess.agentHC(ctx, &id, "terminal.read", p, mapReadParams)
	case "agent.update":
		var par struct {
			ID     string  `json:"id"`
			Title  *string `json:"title,omitempty"`
			Pinned *bool   `json:"pinned,omitempty"`
			Unread *bool   `json:"unread,omitempty"`
		}
		if json.Unmarshal(p, &par) != nil || par.ID == "" {
			sess.writeErr(ctx, &id, "bad_request", "")
			return
		}
		a, ok := sess.srv.Agents.ApplyUserUpdate(par.ID, par.Title, par.Pinned, par.Unread)
		if !ok {
			sess.writeErr(ctx, &id, "not_found", "")
			return
		}
		sess.writeResult(ctx, &id, map[string]any{"agent": a})
	case "agent.start":
		sess.agentStart(ctx, &id, p)
	case "agent.models":
		var par struct {
			Kind string `json:"kind"`
		}
		_ = json.Unmarshal(p, &par)
		switch par.Kind {
		case "claude":
			sess.writeResult(ctx, &id, agents.ClaudeModelsResult())
		case "codex":
			sess.writeResult(ctx, &id, agents.CodexModelsResult())
		case "cursor":
			sess.writeResult(ctx, &id, agents.CursorModelsResult())
		case "opencode":
			sess.writeResult(ctx, &id, sess.openCodeModels(ctx))
		case "fx":
			sess.writeResult(ctx, &id, agents.FxModelsResult())
		case "shell":
			sess.writeResult(ctx, &id, agents.TerminalModelsResult())
		default:
			if agents.IsTerminalLaunchKind(par.Kind) {
				sess.writeResult(ctx, &id, agents.TerminalModelsResult())
			} else if par.Kind == "" {
				sess.writeErr(ctx, &id, "bad_request", "")
			} else {
				sess.writeErr(ctx, &id, "agent_unsupported", "")
			}
		}
	case "agent.kinds":
		sess.writeResult(ctx, &id, agents.InstalledKinds())
	case "agent.configure":
		sess.agentConfigure(ctx, &id, p)
	case "agent.commands":
		sess.agentCommands(ctx, &id, p)
	case "agent.transcript":
		sess.agentTranscript(ctx, &id, p)
	case "agent.watch":
		sess.agentWatch(ctx, &id, p)
	case "term.open":
		sess.termOpen(ctx, &id, p)
	case "term.resize":
		sess.termResize(ctx, &id, p)
	case "term.close":
		sess.termClose(ctx, &id, p)
	case "term.snap":
		sess.termSnap(ctx, &id, p)
	case "inbox.list":
		items := []wire.Item{}
		if sess.srv.Inbox != nil {
			items, _ = sess.srv.Inbox.List()
		}
		sess.writeResult(ctx, &id, wire.InboxListResult{Items: items})
	case "inbox.answer":
		if sess.srv.Inbox == nil {
			sess.writeErr(ctx, &id, "internal", "")
			return
		}
		sess.inboxAnswer(ctx, &id, p)
	case "inbox.read":
		if sess.srv.Inbox == nil {
			sess.writeErr(ctx, &id, "internal", "")
			return
		}
		var par wire.InboxReadParams
		if json.Unmarshal(p, &par) != nil {
			sess.writeErr(ctx, &id, "bad_request", "")
			return
		}
		_ = sess.srv.Inbox.MarkRead(par.IDs)
		sess.writeResult(ctx, &id, map[string]any{})
	case "files.list":
		sess.filesList(ctx, &id, p)
	case "files.read":
		sess.filesRead(ctx, &id, p)
	case "files.mkdir":
		sess.filesMkdir(ctx, &id, p)
	case "files.write":
		sess.filesWrite(ctx, &id, p)
	case "files.upload":
		sess.filesUpload(ctx, &id, p)
	case "files.search":
		sess.filesSearch(ctx, &id, p)
	case "git.status":
		sess.gitStatus(ctx, &id, p)
	case "git.diff":
		sess.gitDiff(ctx, &id, p)
	case "git.commit":
		sess.gitCommit(ctx, &id, p)
	case "usage.summary":
		sess.usageSummary(ctx, &id, p)
	case "servers.list":
		sess.serversList(ctx, &id, p)
	case "servers.kill":
		sess.serversKill(ctx, &id, p)
	case "preview.open":
		sess.previewOpen(ctx, &id, p)
	case "webhooks.list":
		sess.webhooksList(ctx, &id, p)
	case "webhooks.set":
		sess.webhooksSet(ctx, &id, p)
	case "webhooks.remove":
		sess.webhooksRemove(ctx, &id, p)
	case "webhooks.test":
		sess.webhooksTest(ctx, &id, p)
	case "fonts.list":
		sess.fontsList(ctx, &id, p)
	case "fonts.get":
		sess.fontsGet(ctx, &id, p)
	case "panes.list":
		sess.panesList(ctx, &id, p)
	case "panes.peek":
		sess.panesPeek(ctx, &id, p)
	case "panes.spawn":
		sess.panesSpawn(ctx, &id, p)
	default:
		sess.writeErr(ctx, &id, "bad_request", fmt.Sprintf("unknown method %s", method))
	}
}

func mapSendParams(p json.RawMessage) (any, error) {
	var in struct {
		ID   string `json:"id"`
		Text string `json:"text"`
	}
	if err := json.Unmarshal(p, &in); err != nil {
		return nil, err
	}
	return map[string]any{"terminal": in.ID, "text": in.Text}, nil
}

func mapKeysParams(p json.RawMessage) (any, error) {
	var in struct {
		ID   string   `json:"id"`
		Keys []string `json:"keys"`
	}
	if err := json.Unmarshal(p, &in); err != nil {
		return nil, err
	}
	return map[string]any{"terminal": in.ID, "keys": in.Keys}, nil
}

func mapTermOnly(p json.RawMessage) (any, error) {
	var in struct {
		ID       string `json:"id"`
		Terminal string `json:"terminal"`
	}
	if err := json.Unmarshal(p, &in); err != nil {
		return nil, err
	}
	term := strings.TrimSpace(in.Terminal)
	if term == "" {
		term = strings.TrimSpace(in.ID)
	}
	if term == "" {
		return nil, errors.New("missing id")
	}
	return map[string]any{"terminal": term}, nil
}

func mapReadParams(p json.RawMessage) (any, error) {
	var in struct {
		ID    string `json:"id"`
		Lines *int   `json:"lines,omitempty"`
	}
	if err := json.Unmarshal(p, &in); err != nil {
		return nil, err
	}
	m := map[string]any{"terminal": in.ID}
	if in.Lines != nil {
		m["lines"] = *in.Lines
	}
	return m, nil
}

func (sess *session) agentHC(ctx context.Context, id *int64, method string, p json.RawMessage, mapFn func(json.RawMessage) (any, error)) {
	if sess.srv.HC.Status() != hc.StatusUp {
		sess.writeErr(ctx, id, "hc_unavailable", "")
		return
	}
	params, err := mapFn(p)
	if err != nil {
		sess.writeErr(ctx, id, "bad_request", "")
		return
	}
	var out json.RawMessage
	if err := sess.srv.HC.Call(ctx, method, params, &out); err != nil {
		sess.writeErr(ctx, id, "hc_unavailable", err.Error())
		return
	}
	if method == "terminal.read" {
		var rr struct {
			Text string `json:"text"`
		}
		_ = json.Unmarshal(out, &rr)
		sess.writeResult(ctx, id, map[string]any{"text": rr.Text, "rows": 0, "cols": 0})
		return
	}
	if method == "terminal.interrupt" {
		var ir struct {
			Status string `json:"status"`
		}
		_ = json.Unmarshal(out, &ir)
		sess.writeResult(ctx, id, map[string]any{"status": ir.Status})
		return
	}
	sess.writeResult(ctx, id, map[string]any{})
}

func (sess *session) writeResult(ctx context.Context, id *int64, v any) {
	r, _ := json.Marshal(v)
	var frame []byte
	if id == nil {
		frame, _ = json.Marshal(map[string]any{"r": json.RawMessage(r)})
	} else {
		frame, _ = json.Marshal(map[string]any{"id": *id, "r": json.RawMessage(r)})
	}
	if sess.captureWS != nil {
		sess.captureWS(frame)
		return
	}
	sess.mu.Lock()
	_ = sess.conn.Write(ctx, websocket.MessageText, frame)
	sess.mu.Unlock()
}

func (sess *session) writeErr(ctx context.Context, id *int64, kind, msg string) {
	sess.releaseSend(ctx)
	if kind == "internal" {
		log.Printf("rpc internal error: %s", msg)
	}
	if id == nil {
		return
	}
	frame, _ := json.Marshal(wire.WSError{ID: *id, E: wire.ErrorBody{Kind: kind, Msg: msg}})
	if sess.captureWS != nil {
		sess.captureWS(frame)
		return
	}
	sess.mu.Lock()
	_ = sess.conn.Write(ctx, websocket.MessageText, frame)
	sess.mu.Unlock()
}

func ReadBody(r io.Reader, v any) error {
	return json.NewDecoder(r).Decode(v)
}

func optsUsage(s *usage.Summarizer) *usage.Summarizer {
	if s != nil {
		return s
	}
	return usage.NewSummarizer()
}

func optsPreview(p *servers.PreviewStore) *servers.PreviewStore {
	if p != nil {
		return p
	}
	return servers.NewPreviewStore()
}

func (s *Server) handlePreview(w http.ResponseWriter, r *http.Request) {
	if s.Preview == nil {
		http.NotFound(w, r)
		return
	}
	s.Preview.Handler().ServeHTTP(w, r)
}

func (sess *session) markStopped(p json.RawMessage) {
	var par struct {
		ID string `json:"id"`
	}
	if sess.srv.Inbox == nil || json.Unmarshal(p, &par) != nil || par.ID == "" {
		return
	}
	sess.srv.Inbox.MarkStopped(par.ID)
}
