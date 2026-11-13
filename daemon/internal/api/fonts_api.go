package api

import (
	"context"
	"encoding/json"

	"sh.simple/simpled/internal/fonts"
	"sh.simple/simpled/internal/wire"
)

func (sess *session) fontsList(ctx context.Context, id *int64, p json.RawMessage) {
	home := sess.srv.Store.Home
	list, err := fonts.List(home)
	if err != nil {
		sess.writeErr(ctx, id, "internal", "")
		return
	}
	sess.srv.cacheFonts(list)
	sess.writeResult(ctx, id, wire.FontsListResult{Fonts: list})
}

func (sess *session) fontsGet(ctx context.Context, id *int64, p json.RawMessage) {
	var par wire.FontsGetParams
	if json.Unmarshal(p, &par) != nil || par.Path == "" {
		sess.writeErr(ctx, id, "bad_request", "")
		return
	}
	if !fonts.Allowed(sess.srv.fontCache, par.Path) {
		sess.writeErr(ctx, id, "not_found", "")
		return
	}
	sess.writeResult(ctx, id, map[string]any{})
}

func (s *Server) cacheFonts(list []wire.FontEntry) {
	s.mu.Lock()
	s.fontCache = list
	s.mu.Unlock()
}
