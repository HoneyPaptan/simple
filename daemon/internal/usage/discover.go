package usage

import (
	"os"
	"path/filepath"
	"strings"
	"time"
)

func discoverTranscriptPaths(home string, windowStart time.Time) (claude []string, codex []string) {
	if home == "" {
		return nil, nil
	}
	claudeGlob := filepath.Join(home, ".claude", "projects", "*", "*.jsonl")
	if matches, err := filepath.Glob(claudeGlob); err == nil {
		for _, p := range matches {
			if fileModifiedSince(p, windowStart) {
				claude = append(claude, p)
			}
		}
	}
	sessionsRoot := filepath.Join(home, ".codex", "sessions")
	_ = filepath.Walk(sessionsRoot, func(path string, info os.FileInfo, err error) error {
		if err != nil || info == nil || info.IsDir() {
			return nil
		}
		base := filepath.Base(path)
		if !strings.HasPrefix(base, "rollout-") || !strings.HasSuffix(base, ".jsonl") {
			return nil
		}
		if fileModifiedSince(path, windowStart) {
			codex = append(codex, path)
		}
		return nil
	})
	return claude, codex
}

func fileModifiedSince(path string, windowStart time.Time) bool {
	st, err := os.Stat(path)
	if err != nil {
		return false
	}
	return !st.ModTime().UTC().Before(windowStart)
}
