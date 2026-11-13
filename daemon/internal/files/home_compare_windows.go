//go:build windows

package files

import "strings"

func pathWithinHome(home, path string) bool {
	home = strings.TrimRight(home, `\`)
	path = strings.TrimRight(path, `\`)
	if strings.EqualFold(home, path) {
		return true
	}
	prefix := home + `\`
	return len(path) >= len(prefix) && strings.EqualFold(path[:len(prefix)], prefix)
}
