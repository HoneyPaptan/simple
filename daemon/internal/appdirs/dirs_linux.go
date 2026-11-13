//go:build linux

package appdirs

import (
	"fmt"
	"os"
	"path/filepath"
)

func configRoot(home string) string {
	userHome, _ := os.UserHomeDir()
	if d := os.Getenv("XDG_CONFIG_HOME"); d != "" && homeUsesXDG(home, userHome) {
		return d
	}
	if home == "" {
		home = userHome
	}
	return filepath.Join(home, ".config")
}

func stateRoot(home string) string {
	userHome, _ := os.UserHomeDir()
	if d := os.Getenv("XDG_STATE_HOME"); d != "" && homeUsesXDG(home, userHome) {
		return d
	}
	if home == "" {
		home = userHome
	}
	return filepath.Join(home, ".local", "state")
}

func runtimeRoot(home string) string {
	userHome, _ := os.UserHomeDir()
	if d := os.Getenv("XDG_RUNTIME_DIR"); d != "" && homeUsesXDG(home, userHome) {
		return d
	}
	return filepath.Join("/run/user", fmt.Sprint(os.Getuid()))
}

func homeUsesXDG(home, userHome string) bool {
	return home == "" || home == userHome
}

func controlSocket(_, runtime, state string) string {
	_ = state
	return filepath.Join(runtime, "simpled.sock")
}
