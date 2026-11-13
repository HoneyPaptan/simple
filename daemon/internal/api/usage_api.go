package api

import (
	"context"
	"encoding/json"
	"os"

	"sh.simple/simpled/internal/wire"
)

func (sess *session) usageSummary(ctx context.Context, id *int64, p json.RawMessage) {
	var par wire.UsageSummaryParams
	_ = json.Unmarshal(p, &par)
	days := 7
	if par.Days != nil && *par.Days > 0 {
		days = *par.Days
	}
	if days > 90 {
		days = 90
	}
	home, _ := os.UserHomeDir()
	sum := sess.srv.Usage.Summary(home, days)
	sess.writeResult(ctx, id, sum)
}
