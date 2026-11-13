package detect

import (
	"regexp"
	"strings"
)

var shellPromptSuffix = regexp.MustCompile(`[$#]\s*$`)
var shellPromptWithCommand = regexp.MustCompile(`^.+@\S+:\S*\s*[$#]\s*\S`)

func Parse(text string) *Result {
	text = strings.TrimSpace(text)
	if text == "" {
		return nil
	}
	active := activePromptText(text)
	if active == "" {
		return nil
	}
	for _, fn := range []func(string) *Result{
		parseChoice,
		parseYesNo,
		parseContinue,
		parseLink,
		parseInput,
		parseError,
	} {
		if r := fn(active); r != nil {
			if isCommandEcho(r.Title) {
				return nil
			}
			return r
		}
	}
	return nil
}

func activePromptText(full string) string {
	lines := splitLines(full)
	if len(lines) == 0 {
		return ""
	}
	end := len(lines)
	for end > 0 && strings.TrimSpace(lines[end-1]) == "" {
		end--
	}
	if end == 0 {
		return ""
	}
	if isShellPromptLine(strings.TrimSpace(lines[end-1])) {
		return ""
	}
	for i := 0; i < end; i++ {
		if !parseYesNoLine(strings.TrimSpace(lines[i])) {
			continue
		}
		for j := i + 1; j < end; j++ {
			if strings.TrimSpace(lines[j]) != "" {
				return ""
			}
		}
	}
	return strings.Join(lines[:end], "\n")
}

func splitLines(text string) []string {
	raw := strings.Split(text, "\n")
	out := make([]string, 0, len(raw))
	for _, l := range raw {
		out = append(out, strings.TrimRight(l, "\r"))
	}
	return out
}

func isShellPromptLine(line string) bool {
	trim := strings.TrimSpace(line)
	if trim == "" {
		return false
	}
	return shellPromptSuffix.MatchString(trim)
}

func isCommandEcho(line string) bool {
	trim := strings.TrimSpace(line)
	if trim == "" {
		return false
	}
	if isShellPromptLine(trim) {
		return true
	}
	if shellPromptWithCommand.MatchString(trim) {
		return true
	}
	if strings.Contains(trim, "read -p") && strings.Contains(trim, "$") {
		return true
	}
	return false
}

func hasNonEmptyAfter(lines []string, idx, end int) bool {
	for i := idx + 1; i < end; i++ {
		if strings.TrimSpace(lines[i]) != "" {
			return true
		}
	}
	return false
}

func hasOutputAfter(lines []string, lastIdx int) bool {
	return hasNonEmptyAfter(lines, lastIdx, len(lines))
}

func findActivePromptLine(lines []string) int {
	for i := len(lines) - 1; i >= 0; i-- {
		trim := strings.TrimSpace(lines[i])
		if trim == "" {
			continue
		}
		if parseYesNoLine(trim) || parseContinueLine(trim) || parseInputLine(trim) {
			return i
		}
	}
	return -1
}

func choiceBlockStart(lines []string) int {
	lastOpt := -1
	for i, line := range lines {
		if choiceLine.MatchString(line) {
			lastOpt = i
		}
	}
	if lastOpt < 0 {
		return -1
	}
	start := lastOpt
	for start > 0 && choiceLine.MatchString(lines[start-1]) {
		start--
	}
	return start
}

func contextStart(lines []string, promptIdx int) int {
	start := promptIdx
	for start > 0 && isCommandEcho(lines[start-1]) {
		start--
	}
	if start > 0 && isCommandEcho(lines[start-1]) {
		start--
	}
	return start
}

func promptBody(lines []string, promptIdx int) *string {
	var ctx []string
	for i := 0; i < promptIdx; i++ {
		trim := strings.TrimSpace(lines[i])
		if trim == "" || isCommandEcho(trim) {
			continue
		}
		ctx = append(ctx, trim)
	}
	if len(ctx) == 0 {
		return nil
	}
	s := strings.Join(ctx, "\n")
	return &s
}

func parseYesNoLine(line string) bool {
	if isCommandEcho(line) {
		return false
	}
	trim := strings.TrimSpace(line)
	return yesNoBracket.MatchString(trim) || yesNoParen.MatchString(trim)
}

func parseContinueLine(line string) bool {
	lower := strings.ToLower(line)
	return strings.Contains(lower, "press enter") ||
		strings.Contains(lower, "press any key") ||
		strings.Contains(lower, "hit enter")
}

func parseInputLine(line string) bool {
	trim := strings.TrimSpace(line)
	return strings.HasSuffix(trim, "?") || strings.HasSuffix(trim, ":")
}
