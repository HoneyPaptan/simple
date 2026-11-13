package hcsock

import (
	"os"
	"path/filepath"
	"strings"
)

func HerdrSocket() string {
	if p := strings.TrimSpace(os.Getenv("HERDR_SOCKET_PATH")); p != "" {
		return herdrEndpoint(p)
	}
	return herdrEndpoint(filepath.Join(herdrConfigRoot(), "herdr", "herdr.sock"))
}

func herdrConfigRoot() string {
	if d := strings.TrimSpace(os.Getenv("XDG_CONFIG_HOME")); d != "" {
		return d
	}
	return configRoot()
}
