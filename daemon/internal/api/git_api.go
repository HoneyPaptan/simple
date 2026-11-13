package api

import (
	"context"
	"encoding/json"

	"sh.simple/simpled/internal/git"
	"sh.simple/simpled/internal/wire"
)

func (sess *session) gitStatus(ctx context.Context, id *int64, p json.RawMessage) {
	var par wire.GitStatusParams
	if json.Unmarshal(p, &par) != nil || par.Cwd == "" {
		sess.writeErr(ctx, id, "bad_request", "")
		return
	}
	res, err := git.Status(par.Cwd)
	if err != nil {
		sess.writeErr(ctx, id, git.ErrKind(err), "")
		return
	}
	sess.writeResult(ctx, id, res)
}

func (sess *session) gitDiff(ctx context.Context, id *int64, p json.RawMessage) {
	var par wire.GitDiffParams
	if json.Unmarshal(p, &par) != nil || par.Cwd == "" {
		sess.writeErr(ctx, id, "bad_request", "")
		return
	}
	path := ""
	if par.Path != nil {
		path = *par.Path
	}
	res, err := git.Diff(par.Cwd, path)
	if err != nil {
		sess.writeErr(ctx, id, git.ErrKind(err), "")
		return
	}
	sess.writeResult(ctx, id, res)
}

func (sess *session) gitCommit(ctx context.Context, id *int64, p json.RawMessage) {
	var par wire.GitCommitParams
	if json.Unmarshal(p, &par) != nil || par.Cwd == "" {
		sess.writeErr(ctx, id, "bad_request", "")
		return
	}
	hash, err := git.Commit(par.Cwd, par.Message, par.Push)
	if err != nil {
		kind := git.ErrKind(err)
		if kind == "push_failed" {
			sess.writeErr(ctx, id, kind, err.Error())
			return
		}
		sess.writeErr(ctx, id, kind, "")
		return
	}
	sess.writeResult(ctx, id, wire.GitCommitResult{Commit: hash})
}