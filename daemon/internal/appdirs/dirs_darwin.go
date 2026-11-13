//go:build darwin

package appdirs

import (
	"os"
	"path/filepath"
)

func configRoot(home string) string {
	userHome, _ := os.UserHomeDir()
	if d := os.Getenv("XDG_CONFIG_HOME"); d != "" && homeUsesXDG(home, userHome) {
		return d
	}
	if home == "" || home == userHome {
		if d, err := os.UserConfigDir(); err == nil && d != "" {
			return d
		}
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
	return stateRoot(home)
}

func homeUsesXDG(home, userHome string) bool {
	return home == "" || home == userHome
}

func controlSocket(_, _, state string) string {
	return filepath.Join(state, "simpled.sock")
}
