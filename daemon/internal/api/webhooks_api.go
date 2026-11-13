package api

import (
	"context"
	"encoding/json"
	"errors"
	"strconv"

	"sh.simple/simpled/internal/config"
	"sh.simple/simpled/internal/webhooks"
	"sh.simple/simpled/internal/wire"
)

func (sess *session) webhooksList(ctx context.Context, id *int64, p json.RawMessage) {
	cfg, err := sess.srv.Store.LoadConfig()
	if err != nil {
		sess.writeErr(ctx, id, "internal", "")
		return
	}
	out := make([]wire.WebhookView, 0, len(cfg.Webhooks))
	for _, h := range cfg.Webhooks {
		out = append(out, wire.WebhookView{ID: h.ID, URL: h.URL, Kinds: h.Kinds})
	}
	sess.writeResult(ctx, id, wire.WebhooksListResult{Webhooks: out})
}

func (sess *session) webhooksSet(ctx context.Context, id *int64, p json.RawMessage) {
	var par wire.WebhooksSetParams
	if json.Unmarshal(p, &par) != nil || par.URL == "" || len(par.Kinds) == 0 {
		sess.writeErr(ctx, id, "bad_request", "")
		return
	}
	if !webhooks.ValidURL(par.URL) {
		sess.writeErr(ctx, id, "bad_request", "")
		return
	}
	cfg, err := sess.srv.Store.LoadConfig()
	if err != nil {
		sess.writeErr(ctx, id, "internal", "")
		return
	}
	secret, err := webhooks.NewSecret()
	if err != nil {
		sess.writeErr(ctx, id, "internal", "")
		return
	}
	hookID := ""
	if par.ID != nil {
		hookID = *par.ID
	}
	if hookID == "" {
		hookID, err = webhooks.NewID()
		if err != nil {
			sess.writeErr(ctx, id, "internal", "")
			return
		}
	} else {
		found := false
		for i, h := range cfg.Webhooks {
			if h.ID == hookID {
				secret = h.Secret
				cfg.Webhooks[i].URL = par.URL
				cfg.Webhooks[i].Kinds = par.Kinds
				found = true
				break
			}
		}
		if !found {
			sess.writeErr(ctx, id, "not_found", "")
			return
		}
		if err := sess.srv.Store.SaveConfig(cfg); err != nil {
			sess.writeErr(ctx, id, "internal", "")
			return
		}
		sess.writeResult(ctx, id, wire.WebhooksSetResult{
			Webhook: wire.WebhookView{ID: hookID, URL: par.URL, Kinds: par.Kinds},
			Secret:  "",
		})
		return
	}
	cfg.Webhooks = append(cfg.Webhooks, config.Webhook{ID: hookID, URL: par.URL, Secret: secret, Kinds: par.Kinds})
	if err := sess.srv.Store.SaveConfig(cfg); err != nil {
		sess.writeErr(ctx, id, "internal", "")
		return
	}
	sess.writeResult(ctx, id, wire.WebhooksSetResult{
		Webhook: wire.WebhookView{ID: hookID, URL: par.URL, Kinds: par.Kinds},
		Secret:  secret,
	})
}

func (sess *session) webhooksRemove(ctx context.Context, id *int64, p json.RawMessage) {
	var par wire.WebhooksRemoveParams
	if json.Unmarshal(p, &par) != nil || par.ID == "" {
		sess.writeErr(ctx, id, "bad_request", "")
		return
	}
	cfg, err := sess.srv.Store.LoadConfig()
	if err != nil {
		sess.writeErr(ctx, id, "internal", "")
		return
	}
	out := cfg.Webhooks[:0]
	found := false
	for _, h := range cfg.Webhooks {
		if h.ID == par.ID {
			found = true
			continue
		}
		out = append(out, h)
	}
	if !found {
		sess.writeErr(ctx, id, "not_found", "")
		return
	}
	cfg.Webhooks = out
	if err := sess.srv.Store.SaveConfig(cfg); err != nil {
		sess.writeErr(ctx, id, "internal", "")
		return
	}
	sess.writeResult(ctx, id, map[string]any{})
}

func (sess *session) webhooksTest(ctx context.Context, id *int64, p json.RawMessage) {
	var par wire.WebhooksTestParams
	if json.Unmarshal(p, &par) != nil || par.ID == "" {
		sess.writeErr(ctx, id, "bad_request", "")
		return
	}
	cfg, err := sess.srv.Store.LoadConfig()
	if err != nil {
		sess.writeErr(ctx, id, "internal", "")
		return
	}
	var hook *config.Webhook
	for i := range cfg.Webhooks {
		if cfg.Webhooks[i].ID == par.ID {
			hook = &cfg.Webhooks[i]
			break
		}
	}
	if hook == nil {
		sess.writeErr(ctx, id, "not_found", "")
		return
	}
	d := sess.srv.Webhooks
	if d == nil {
		sess.writeErr(ctx, id, "internal", "")
		return
	}
	if err := d.SendSample(*hook); err != nil {
		kind, msg := webhookTestError(err)
		sess.writeErr(ctx, id, kind, msg)
		return
	}
	sess.writeResult(ctx, id, map[string]any{})
}

func webhookTestError(err error) (string, string) {
	var status webhooks.StatusError
	switch {
	case errors.Is(err, webhooks.ErrTimeout):
		return "timeout", ""
	case errors.Is(err, webhooks.ErrUnreachable):
		return "webhook_unreachable", ""
	case errors.As(err, &status):
		return "webhook_status", strconv.Itoa(status.Code)
	default:
		return "bad_request", ""
	}
}
