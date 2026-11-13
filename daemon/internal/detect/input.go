package detect

import (
	"strings"
)

func parseInput(text string) *Result {
	lines := splitLines(text)
	if len(lines) == 0 {
		return nil
	}
	if parseYesNo(text) != nil || parseContinue(text) != nil || parseChoice(text) != nil {
		return nil
	}
	promptIdx := -1
	for i := len(lines) - 1; i >= 0; i-- {
		line := strings.TrimSpace(lines[i])
		if parseInputLine(line) {
			promptIdx = i
			break
		}
	}
	if promptIdx < 0 && len(lines) >= 2 {
		prev := strings.TrimSpace(lines[len(lines)-2])
		if parseInputLine(prev) && !isShellPromptLine(strings.TrimSpace(lines[len(lines)-1])) {
			promptIdx = len(lines) - 2
		}
	}
	if promptIdx < 0 {
		return nil
	}
	title := strings.TrimSpace(lines[promptIdx])
	return &Result{Detector: "input", Title: title, Body: promptBody(lines, promptIdx)}
}
