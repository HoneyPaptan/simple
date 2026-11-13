package detect

import (
	"regexp"
	"strings"
)

var yesNoBracket = regexp.MustCompile(`(?i)\[(y/n|Y/n|y/N|yes/no)\]`)
var yesNoParen = regexp.MustCompile(`(?i)\((Y/n|y/N|yes/no)\)`)
var yesNoHint = regexp.MustCompile(`(?i)\s*[\[(](y/n|Y/n|y/N|yes/no)[)\]]\s*$`)

func parseYesNo(text string) *Result {
	lines := splitLines(text)
	if len(lines) == 0 {
		return nil
	}
	promptIdx := -1
	for i := len(lines) - 1; i >= 0; i-- {
		if parseYesNoLine(strings.TrimSpace(lines[i])) {
			promptIdx = i
			break
		}
	}
	if promptIdx < 0 {
		return nil
	}
	title := stripYesNoHint(strings.TrimSpace(lines[promptIdx]))
	return &Result{Detector: "yesno", Title: title, Body: promptBody(lines, promptIdx)}
}

func stripYesNoHint(line string) string {
	line = yesNoHint.ReplaceAllString(line, "")
	line = yesNoParen.ReplaceAllString(line, "")
	return strings.TrimSpace(line)
}
