package files

import (
	"os"
	"path/filepath"
	"strings"
)

func ResolvePath(raw string) (string, error) {
	s := strings.TrimSpace(raw)
	if s == "" {
		return "", errBadRequest
	}
	if strings.HasPrefix(s, "~") {
		home, err := os.UserHomeDir()
		if err != nil {
			return "", errInternal
		}
		if s == "~" {
			s = home
		} else if strings.HasPrefix(s, "~/") {
			s = filepath.Join(home, s[2:])
		} else {
			return "", errBadRequest
		}
	}
	if !filepath.IsAbs(s) {
		return "", errBadRequest
	}
	clean := filepath.Clean(s)
	if clean == "." || clean == string(filepath.Separator) {
		return clean, nil
	}
	return clean, nil
}
