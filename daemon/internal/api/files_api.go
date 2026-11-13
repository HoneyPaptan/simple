package api

import (
	"context"
	"encoding/json"

	"sh.simple/simpled/internal/files"
	"sh.simple/simpled/internal/wire"
)

func (sess *session) filesList(ctx context.Context, id *int64, p json.RawMessage) {
	var par wire.FilesListParams
	if json.Unmarshal(p, &par) != nil || par.Path == "" {
		sess.writeErr(ctx, id, "bad_request", "")
		return
	}
	res, err := files.List(par.Path)
	if err != nil {
		sess.writeErr(ctx, id, files.ErrKind(err), "")
		return
	}
	sess.writeResult(ctx, id, res)
}

func (sess *session) filesRead(ctx context.Context, id *int64, p json.RawMessage) {
	var par wire.FilesReadParams
	if json.Unmarshal(p, &par) != nil || par.Path == "" {
		sess.writeErr(ctx, id, "bad_request", "")
		return
	}
	res, err := files.Read(par.Path, par.Offset, par.Limit)
	if err != nil {
		sess.writeErr(ctx, id, files.ErrKind(err), "")
		return
	}
	sess.writeResult(ctx, id, res)
}

func (sess *session) filesMkdir(ctx context.Context, id *int64, p json.RawMessage) {
	var par wire.FilesMkdirParams
	if json.Unmarshal(p, &par) != nil || par.Path == "" || par.Name == "" {
		sess.writeErr(ctx, id, "bad_request", "")
		return
	}
	if err := files.Mkdir(par.Path, par.Name); err != nil {
		sess.writeErr(ctx, id, files.ErrKind(err), "")
		return
	}
	sess.writeResult(ctx, id, map[string]any{})
}

func (sess *session) filesWrite(ctx context.Context, id *int64, p json.RawMessage) {
	var par wire.FilesWriteParams
	if json.Unmarshal(p, &par) != nil || par.Path == "" {
		sess.writeErr(ctx, id, "bad_request", "")
		return
	}
	if err := files.Write(par.Path, par.Text); err != nil {
		sess.writeErr(ctx, id, files.ErrKind(err), "")
		return
	}
	sess.writeResult(ctx, id, map[string]any{})
}

func (sess *session) filesSearch(ctx context.Context, id *int64, p json.RawMessage) {
	var par wire.FilesSearchParams
	if json.Unmarshal(p, &par) != nil || par.Cwd == "" {
		sess.writeErr(ctx, id, "bad_request", "")
		return
	}
	res, err := files.Search(par.Cwd, par.Query, par.Limit)
	if err != nil {
		sess.writeErr(ctx, id, files.ErrKind(err), "")
		return
	}
	sess.writeResult(ctx, id, res)
}

func (sess *session) filesUpload(ctx context.Context, id *int64, p json.RawMessage) {
	var par wire.FilesUploadParams
	if json.Unmarshal(p, &par) != nil || par.Workspace == "" || par.Ext == "" {
		sess.writeErr(ctx, id, "bad_request", "")
		return
	}
	path, err := files.ReserveAttachmentPath(par.Workspace, par.Ext)
	if err != nil {
		sess.writeErr(ctx, id, files.ErrKind(err), "")
		return
	}
	sess.writeResult(ctx, id, wire.FilesUploadResult{Path: path})
}
