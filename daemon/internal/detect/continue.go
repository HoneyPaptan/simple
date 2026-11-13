package detect

import (
	"strings"
)

func parseContinue(text string) *Result {
	lines := splitLines(text)
	for i := len(lines) - 1; i >= 0; i-- {
		line := strings.TrimSpace(lines[i])
		if !parseContinueLine(line) {
			continue
		}
		return &Result{Detector: "continue", Title: line, Body: promptBody(lines, i)}
	}
	return nil
}
