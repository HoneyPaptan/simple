package detect

import (
	"strings"
)

func parseError(text string) *Result {
	lines := nonEmptyTail(text, 6)
	for i := len(lines) - 1; i >= 0; i-- {
		line := lines[i]
		lower := strings.ToLower(line)
		if strings.HasPrefix(lower, "error:") ||
			strings.HasPrefix(lower, "fatal:") ||
			strings.Contains(lower, "error:") && i == len(lines)-1 {
			body := strings.Join(lines, "\n")
			title := line
			return &Result{Detector: "error", Title: title, Body: &body}
		}
	}
	return nil
}

func nonEmptyTail(text string, max int) []string {
	lines := strings.Split(text, "\n")
	out := make([]string, 0, max)
	for i := len(lines) - 1; i >= 0 && len(out) < max; i-- {
		t := strings.TrimSpace(lines[i])
		if t == "" {
			continue
		}
		out = append([]string{t}, out...)
	}
	return out
}

func lastLines(text string, n int) string {
	lines := nonEmptyTail(text, n)
	return strings.Join(lines, "\n")
}
