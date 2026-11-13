package usage

import (
	"sh.simple/simpled/internal/transcript"
)

type transcriptUsageCache struct {
	inner *transcript.UsageCache
}

func newTranscriptUsageCache() *transcriptUsageCache {
	return &transcriptUsageCache{inner: transcript.NewUsageCache()}
}

func (c *transcriptUsageCache) events(path, kind string) ([]transcript.UsageEvent, error) {
	return c.inner.Events(path, kind)
}
