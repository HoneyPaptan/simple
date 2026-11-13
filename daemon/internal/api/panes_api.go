package api

import (
	"context"
	"encoding/json"
	"sh.simple/simpled/internal/files"

	"sh.simple/simpled/internal/panes"
	"sh.simple/simpled/internal/wire"
)

func (sess *session) panesList(ctx context.Context, id *int64, p json.RawMessage) {
	var par wire.PanesListParams
	if json.Unmarshal(p, &par) != nil || par.Workspace == "" {
		sess.writeErr(ctx, id, "bad_request", "")
		return
	}
	svc := panes.New(sess.srv.Agents, sess.srv.HC, sess.srv.Herdr)
	res, err := svc.List(ctx, par.Workspace)
	if err != nil {
		sess.writeErr(ctx, id, "hc_unavailable", err.Error())
		return
	}
	sess.writeResult(ctx, id, res)
}

func (sess *session) panesSpawn(ctx context.Context, id *int64, p json.RawMessage) {
	var par wire.PanesSpawnParams
	if json.Unmarshal(p, &par) != nil || par.Workspace == "" {
		sess.writeErr(ctx, id, "bad_request", "")
		return
	}
	workspace, werr := files.ResolvePath(par.Workspace)
	if werr != nil {
		sess.writeErr(ctx, id, "bad_request", "")
		return
	}
	svc := panes.New(sess.srv.Agents, sess.srv.HC, sess.srv.Herdr)
	paneID, err := svc.Spawn(ctx, workspace)
	if err != nil {
		sess.writeErr(ctx, id, "hc_unavailable", err.Error())
		return
	}
	sess.writeResult(ctx, id, wire.PanesSpawnResult{ID: paneID})
}

func (sess *session) panesPeek(ctx context.Context, id *int64, p json.RawMessage) {
	var par wire.PanesPeekParams
	if json.Unmarshal(p, &par) != nil || len(par.IDs) == 0 {
		sess.writeErr(ctx, id, "bad_request", "")
		return
	}
	lines := 0
	if par.Lines != nil {
		lines = *par.Lines
	}
	svc := panes.New(sess.srv.Agents, sess.srv.HC, sess.srv.Herdr)
	res, err := svc.Peek(ctx, par.IDs, lines)
	if err != nil {
		sess.writeErr(ctx, id, "hc_unavailable", err.Error())
		return
	}
	sess.writeResult(ctx, id, res)
}
