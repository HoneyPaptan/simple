package agents

import "strings"

func toSlash(path string) string {
	return strings.ReplaceAll(path, `\`, `/`)
}
