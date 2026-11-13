//go:build windows

package appdirs

import (
	"os"
	"path/filepath"
)

func configRoot(home string) string {
	if d, err := os.UserConfigDir(); err == nil && d != "" {
		return d
	}
	return filepath.Join(home, "AppData", "Roaming")
}

func stateRoot(home string) string {
	if d := os.Getenv("LOCALAPPDATA"); d != "" {
		return d
	}
	return filepath.Join(home, "AppData", "Local")
}

func runtimeRoot(home string) string {
	return stateRoot(home)
}

func controlSocket(home, _, state string) string {
	_ = home
	return filepath.Join(state, "simpled", "simpled.sock")
}
