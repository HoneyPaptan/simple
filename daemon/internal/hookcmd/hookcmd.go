package hookcmd

import "strings"

func Line(goos, simpledPath, kind, event string) string {
	return quote(goos, simpledPath) + " hook " + kind + " " + event
}

func quote(goos, path string) string {
	if goos == "windows" {
		path = strings.ReplaceAll(path, `\`, "/")
		return `"` + strings.ReplaceAll(path, `"`, `\"`) + `"`
	}
	return "'" + strings.ReplaceAll(path, "'", `'\''`) + "'"
}
