package appdirs

import (
	"os"
	"path/filepath"
)

func ConfigDir(home string) string {
	if home == "" {
		home, _ = os.UserHomeDir()
	}
	return filepath.Join(configRoot(home), "simpled")
}

func StateDir(home string) string {
	if home == "" {
		home, _ = os.UserHomeDir()
	}
	return filepath.Join(stateRoot(home), "simpled")
}

func RuntimeDir(home string) string {
	if home == "" {
		home, _ = os.UserHomeDir()
	}
	return runtimeRoot(home)
}

func ControlSocket(home string) string {
	if home == "" {
		home, _ = os.UserHomeDir()
	}
	rt := runtimeRoot(home)
	st := stateRoot(home)
	return controlSocket(home, rt, st)
}

func OpenCodeStatePath(home string) string {
	xdg := ""
	if home == "" {
		home, _ = os.UserHomeDir()
		xdg = os.Getenv("XDG_STATE_HOME")
	}
	return filepath.Join(OpenCodeStateRoot(home, xdg), "opencode", "service.json")
}

func OpenCodeStateRoot(home, xdgStateHome string) string {
	if xdgStateHome != "" {
		return xdgStateHome
	}
	return filepath.Join(home, ".local", "state")
}
