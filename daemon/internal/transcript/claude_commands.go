package transcript

import (
	"encoding/json"
	"regexp"
	"strings"

	"sh.simple/simpled/internal/wire"
)

var (
	commandNameRe = regexp.MustCompile(`(?s)<command-name>(.*?)</command-name>`)
	commandArgsRe = regexp.MustCompile(`(?s)<command-args>(.*?)</command-args>`)
	commandOutRe  = regexp.MustCompile(`(?s)<local-command-std(?:out|err)>(.*?)</local-command-std(?:out|err)>`)
	ansiRe        = regexp.MustCompile(`\x1b\[[0-9;?]*[A-Za-z]`)
)

func slashCommandText(text string) string {
	name := commandNameRe.FindStringSubmatch(text)
	if name == nil {
		return ""
	}
	out := strings.TrimSpace(name[1])
	if args := commandArgsRe.FindStringSubmatch(text); args != nil && strings.TrimSpace(args[1]) != "" {
		out += " " + strings.TrimSpace(args[1])
	}
	return out
}

func localCommandMessage(row claudeLine) (wire.Message, bool) {
	var content string
	if json.Unmarshal(row.Content, &content) != nil || row.UUID == "" {
		return wire.Message{}, false
	}
	ts := parseTS(row.Timestamp)
	if cmd := slashCommandText(content); cmd != "" {
		return wire.Message{ID: row.UUID, Role: "user", Ts: ts, Text: &cmd}, true
	}
	m := commandOutRe.FindStringSubmatch(content)
	if m == nil {
		return wire.Message{}, false
	}
	body := strings.TrimRight(ansiRe.ReplaceAllString(m[1], ""), " \n")
	body = strings.Trim(body, "\n")
	if strings.TrimSpace(body) == "" {
		return wire.Message{}, false
	}
	text := "```\n" + body + "\n```"
	return wire.Message{ID: row.UUID, Role: "assistant", Ts: ts, Text: &text}, true
}
