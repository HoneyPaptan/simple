package detect

import (
	"regexp"
	"strings"

	"sh.simple/simpled/internal/wire"
)

var choiceLine = regexp.MustCompile(`(?m)^\s*(?:>\s*)?(\d+)[.)]\s+(.+)\s*$`)

func parseChoice(text string) *Result {
	lines := splitLines(text)
	var opts []wire.Option
	for _, line := range lines {
		m := choiceLine.FindStringSubmatch(line)
		if m == nil {
			continue
		}
		opts = append(opts, wire.Option{ID: m[1], Label: strings.TrimSpace(m[2])})
	}
	if len(opts) < 2 {
		return nil
	}
	title := choiceTitleAboveOptions(lines)
	promptIdx := len(lines) - 1
	for promptIdx >= 0 && choiceLine.MatchString(lines[promptIdx]) {
		promptIdx--
	}
	if promptIdx >= 0 {
		trim := strings.TrimSpace(lines[promptIdx])
		if trim != "" && !choiceLine.MatchString(lines[promptIdx]) && hasLetters(trim) {
			title = trim
		}
	}
	return &Result{Detector: "choice", Title: title, Body: promptBody(lines, firstChoiceLine(lines)), Options: opts}
}

func choiceTitleAboveOptions(lines []string) string {
	start := choiceBlockStart(lines)
	if start < 0 {
		return "Choose an option"
	}
	for i := start - 1; i >= 0; i-- {
		trim := strings.TrimSpace(lines[i])
		if trim == "" || choiceLine.MatchString(lines[i]) || isCommandEcho(trim) {
			continue
		}
		if hasLetters(trim) {
			return trim
		}
	}
	return "Choose an option"
}

func hasLetters(s string) bool {
	for _, r := range s {
		if (r >= 'a' && r <= 'z') || (r >= 'A' && r <= 'Z') {
			return true
		}
	}
	return false
}

func firstChoiceLine(lines []string) int {
	for i, line := range lines {
		if choiceLine.MatchString(line) {
			return i
		}
	}
	return 0
}
