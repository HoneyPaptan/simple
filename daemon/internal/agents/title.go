package agents

import "strings"

const promptTitleMax = 40

func CompactPromptTitle(s string) string {
	s = strings.TrimSpace(s)
	if i := strings.IndexByte(s, '\n'); i >= 0 {
		s = strings.TrimSpace(s[:i])
	}
	if s == "" {
		return ""
	}
	if len(s) <= promptTitleMax {
		return s
	}
	return s[:promptTitleMax-1] + "…"
}

func DefaultKindTitle(kind, title string) bool {
	return strings.TrimSpace(title) == KindLabel(kind)
}
