package detect

import (
	"net/url"
	"regexp"
	"strings"
)

var httpURL = regexp.MustCompile(`https?://[^\s\]]+`)

func parseLink(text string) *Result {
	lines := nonEmptyTail(text, 8)
	for _, line := range lines {
		lower := strings.ToLower(line)
		if !strings.Contains(lower, "http") &&
			!strings.Contains(lower, "login") &&
			!strings.Contains(lower, "auth") &&
			!strings.Contains(lower, "verify") {
			continue
		}
		loc := httpURL.FindString(line)
		if loc == "" {
			continue
		}
		if _, err := url.Parse(loc); err != nil {
			continue
		}
		link := loc
		body := strings.Join(lines, "\n")
		title := "Open the link to continue"
		if strings.Contains(lower, "login") {
			title = "Login required"
		}
		return &Result{Detector: "link", Title: title, Body: &body, Link: &link}
	}
	return nil
}
