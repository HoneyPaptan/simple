package files

import (
	"os"
	"path/filepath"
)

func ResolvePathUnderHome(raw string) (string, error) {
	path, err := ResolvePath(raw)
	if err != nil {
		return "", err
	}
	home, err := os.UserHomeDir()
	if err != nil {
		return "", errInternal
	}
	home = filepath.Clean(home)
	if real, err := filepath.EvalSymlinks(home); err == nil {
		home = real
	}
	if real, err := filepath.EvalSymlinks(path); err == nil {
		path = real
	}
	if pathWithinHome(home, path) {
		return path, nil
	}
	return "", errBadRequest
}
