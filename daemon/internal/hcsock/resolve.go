package hcsock

import (
	"os"
	"path/filepath"
	"strings"
)

const defaultSession = "default"

func Session() string {
	s := strings.TrimSpace(os.Getenv("HC_SESSION"))
	if s == "" {
		return defaultSession
	}
	return s
}

func EnvOverride() string {
	if p := strings.TrimSpace(os.Getenv("HC_SOCKET")); p != "" {
		return p
	}
	return ""
}

func ConfigPath() string {
	return filepath.Join(configRoot(), "herdr-core", "config.toml")
}

func Resolve() string {
	if p := EnvOverride(); p != "" {
		return p
	}
	if p := socketFromConfig(ConfigPath()); p != "" {
		return p
	}
	return defaultSocket(Session())
}

func socketFromConfig(path string) string {
	b, err := os.ReadFile(path)
	if err != nil {
		return ""
	}
	for _, line := range strings.Split(string(b), "\n") {
		line = strings.TrimSpace(line)
		key, val, ok := strings.Cut(line, "=")
		if ok && strings.TrimSpace(key) == "socket" {
			v := strings.TrimSpace(val)
			v = strings.Trim(v, `"'`)
			if v != "" {
				return v
			}
		}
	}
	return ""
}

func IsNamedPipe(path string) bool {
	return strings.HasPrefix(path, `\\.\pipe\`) || strings.HasPrefix(path, `\\?\pipe\`)
}
