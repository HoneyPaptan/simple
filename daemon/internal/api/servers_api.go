package api

import (
	"context"
	"encoding/json"
	"os"

	"sh.simple/simpled/internal/servers"
	"sh.simple/simpled/internal/wire"
)

func (s *Server) currentServers() ([]servers.Entry, error) {
	exclude := map[int]struct{}{s.ListenPort: {}}
	return servers.List(servers.ListOptions{ExcludePorts: exclude})
}

func (sess *session) serversList(ctx context.Context, id *int64, p json.RawMessage) {
	list, err := sess.srv.currentServers()
	if err != nil {
		sess.writeErr(ctx, id, "internal", "")
		return
	}
	out := make([]wire.ServerEntry, len(list))
	for i, e := range list {
		out[i] = wire.ServerEntry{Port: e.Port, PID: e.PID, Command: e.Command, Cwd: e.Cwd}
	}
	sess.writeResult(ctx, id, wire.ServersListResult{Servers: out})
}

func (sess *session) serversKill(ctx context.Context, id *int64, p json.RawMessage) {
	var par wire.ServersKillParams
	if json.Unmarshal(p, &par) != nil || par.PID <= 0 {
		sess.writeErr(ctx, id, "bad_request", "")
		return
	}
	list, err := sess.srv.currentServers()
	if err != nil {
		sess.writeErr(ctx, id, "internal", "")
		return
	}
	if err := servers.KillListed(list, par.PID); err != nil {
		if os.IsNotExist(err) {
			sess.writeErr(ctx, id, "not_found", "")
			return
		}
		sess.writeErr(ctx, id, "denied", "")
		return
	}
	sess.writeResult(ctx, id, map[string]any{})
}

func (sess *session) previewOpen(ctx context.Context, id *int64, p json.RawMessage) {
	var par wire.PreviewOpenParams
	if json.Unmarshal(p, &par) != nil || par.Port <= 0 || par.Port > 65535 {
		sess.writeErr(ctx, id, "bad_request", "")
		return
	}
	list, err := sess.srv.currentServers()
	if err != nil {
		sess.writeErr(ctx, id, "internal", "")
		return
	}
	found := false
	for _, e := range list {
		if e.Port == par.Port {
			found = true
			break
		}
	}
	if !found {
		sess.writeErr(ctx, id, "not_found", "")
		return
	}
	if sess.srv.Preview == nil {
		sess.writeErr(ctx, id, "internal", "")
		return
	}
	path, exp, session := sess.srv.Preview.Open(par.Port)
	sess.writeResult(ctx, id, wire.PreviewOpenResult{Path: path, ExpiresMs: exp, Session: session})
}
