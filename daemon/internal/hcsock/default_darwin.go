//go:build darwin

package hcsock

import (
	"path/filepath"
)

func defaultSocket(session string) string {
	return filepath.Join(stateRoot(), "herdr-core", session, session+".sock")
}
