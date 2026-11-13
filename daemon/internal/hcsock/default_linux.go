//go:build linux

package hcsock

import (
	"fmt"
	"os"
	"path/filepath"
)

func defaultSocket(session string) string {
	if rt := os.Getenv("XDG_RUNTIME_DIR"); rt != "" {
		return filepath.Join(rt, "herdr-core", session+".sock")
	}
	uid := os.Getuid()
	fallback := filepath.Join("/run/user", fmt.Sprint(uid), "herdr-core")
	if st, err := os.Stat(fallback); err == nil && st.IsDir() {
		return filepath.Join(fallback, session+".sock")
	}
	return filepath.Join(stateRoot(), "herdr-core", session, session+".sock")
}
