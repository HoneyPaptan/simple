package files

import (
	"errors"
	"os"
	"path/filepath"
	"strings"
)

func Mkdir(rawParent, name string) error {
	if err := validateDirName(name); err != nil {
		return err
	}
	parent, err := ResolvePathUnderHome(rawParent)
	if err != nil {
		if errors.Is(err, errBadRequest) {
			return errOutsideHome
		}
		return err
	}
	st, err := os.Stat(parent)
	if err != nil {
		if os.IsNotExist(err) {
			return errNotFound
		}
		if os.IsPermission(err) {
			return errDenied
		}
		return errInternal
	}
	if !st.IsDir() {
		return errBadRequest
	}
	target := filepath.Join(parent, name)
	if realParent, err := filepath.EvalSymlinks(parent); err == nil {
		parent = realParent
		target = filepath.Join(parent, name)
	}
	if _, err := ResolvePathUnderHome(target); err != nil {
		if errors.Is(err, errBadRequest) {
			return errOutsideHome
		}
		return err
	}
	if err := os.Mkdir(target, 0o755); err != nil {
		if os.IsExist(err) {
			return errExists
		}
		if os.IsPermission(err) {
			return errDenied
		}
		return errInternal
	}
	return nil
}

func validateDirName(name string) error {
	if name == "" || strings.ContainsRune(name, 0) {
		return errInvalidName
	}
	if strings.ContainsAny(name, `/\`) {
		return errInvalidName
	}
	if name == "." || name == ".." {
		return errInvalidName
	}
	if filepath.Clean(name) != name || strings.Contains(name, string(os.PathSeparator)) {
		return errInvalidName
	}
	return nil
}
