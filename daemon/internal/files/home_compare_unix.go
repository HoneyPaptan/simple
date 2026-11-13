//go:build !windows

package files

import (
	"path/filepath"
	"strings"
)

func pathWithinHome(home, path string) bool {
	return path == home || strings.HasPrefix(path, home+string(filepath.Separator))
}
