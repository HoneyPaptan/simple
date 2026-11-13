package usage

import (
	"time"

	"sh.simple/simpled/internal/transcript"
	"sh.simple/simpled/internal/wire"
)

type AgentSource struct {
	Agent wire.Agent
	Path  string
}

type Summarizer struct {
	cache *transcriptUsageCache
}

func NewSummarizer() *Summarizer {
	return &Summarizer{cache: newTranscriptUsageCache()}
}

func (s *Summarizer) Summary(home string, days int) wire.UsageSummaryResult {
	if days <= 0 {
		days = 7
	}
	now := time.Now().UTC()
	todayKey := now.Format("2006-01-02")
	start := now.AddDate(0, 0, -(days - 1))
	startDay := time.Date(start.Year(), start.Month(), start.Day(), 0, 0, 0, 0, time.UTC)
	startMs := startDay.UnixMilli()

	dailyMap := map[string]*wire.UsageBucket{}
	for i := 0; i < days; i++ {
		d := startDay.AddDate(0, 0, i).Format("2006-01-02")
		dailyMap[d] = &wire.UsageBucket{}
	}

	var today wire.UsageBucket
	var period wire.UsageBucket
	claudePaths, codexPaths := discoverTranscriptPaths(home, startDay)

	kinds := []struct {
		id    string
		title string
		paths []string
		kind  string
	}{
		{id: "claude", title: "Claude", paths: claudePaths, kind: "claude"},
		{id: "codex", title: "Codex", paths: codexPaths, kind: "codex"},
	}
	agentRows := make([]wire.UsageAgentRow, 0, len(kinds))

	for _, block := range kinds {
		var agentTotal wire.UsageBucket
		hasData := false
		for _, path := range block.paths {
			events, _ := s.cache.events(path, block.kind)
			for _, ev := range events {
				if ev.TsMs < startMs {
					continue
				}
				hasData = true
				day := transcriptDayUTC(ev.TsMs)
				bucket := dailyMap[day]
				if bucket == nil {
					bucket = &wire.UsageBucket{}
					dailyMap[day] = bucket
				}
				addBucket(bucket, ev)
				addBucket(&agentTotal, ev)
				addBucket(&period, ev)
				if day == todayKey {
					addBucket(&today, ev)
				}
			}
		}
		agentRows = append(agentRows, wire.UsageAgentRow{
			ID:      block.id,
			Kind:    block.kind,
			Title:   block.title,
			HasData: hasData,
			Total:   agentTotal,
		})
	}

	daily := make([]wire.UsageDayRow, 0, days)
	for i := 0; i < days; i++ {
		d := startDay.AddDate(0, 0, i).Format("2006-01-02")
		b := dailyMap[d]
		if b == nil {
			b = &wire.UsageBucket{}
		}
		daily = append(daily, wire.UsageDayRow{Day: d, UsageBucket: *b})
	}

	return wire.UsageSummaryResult{
		Days:   days,
		Today:  today,
		Period: period,
		Daily:  daily,
		Agents: agentRows,
	}
}

func addBucket(b *wire.UsageBucket, ev transcript.UsageEvent) {
	b.Input += ev.Input
	b.Output += ev.Output
	b.CacheRead += ev.CacheRead
	b.CacheWrite += ev.CacheWrite
	if ev.CostUSD != nil {
		if b.CostUSD == nil {
			v := *ev.CostUSD
			b.CostUSD = &v
		} else {
			*b.CostUSD += *ev.CostUSD
		}
	}
}

func transcriptDayUTC(ms int64) string {
	return time.UnixMilli(ms).UTC().Format("2006-01-02")
}
