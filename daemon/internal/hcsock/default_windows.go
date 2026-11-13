//go:build windows

package hcsock

import (
	"os"
	"path/filepath"
)

func defaultSocket(session string) string {
	return WindowsDefaultPipe(os.Getenv("USERNAME"), session)
}

func configRoot() string {
	if d := os.Getenv("APPDATA"); d != "" {
		return d
	}
	home, _ := os.UserHomeDir()
	if home == "" {
		return ""
	}
	return filepath.Join(home, "AppData", "Roaming")
}

func stateRoot() string {
	if d := os.Getenv("LOCALAPPDATA"); d != "" {
		return d
	}
	home, _ := os.UserHomeDir()
	if home == "" {
		return ""
	}
	return filepath.Join(home, "AppData", "Local")
}
