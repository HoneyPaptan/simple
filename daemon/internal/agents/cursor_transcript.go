package agents

import (
	"os"
	"path/filepath"
	"strings"
	"time"
)

func CursorProjectDir(cwd string) string {
	home, err := os.UserHomeDir()
	if err != nil {
		return ""
	}
	return filepath.Join(home, ".cursor", "projects", CursorProjectSlug(cwd))
}

func CursorProjectSlug(cwd string) string {
	s := strings.ReplaceAll(toSlash(filepath.Clean(cwd)), "/", "-")
	for strings.Contains(s, "--") {
		s = strings.ReplaceAll(s, "--", "-")
	}
	return strings.Trim(s, "-")
}

func FirstCursorTranscript(cwd string, afterMs int64, claimed map[string]struct{}) (path, session string) {
	dir := filepath.Join(CursorProjectDir(cwd), "agent-transcripts")
	entries, err := os.ReadDir(dir)
	if err != nil {
		return "", ""
	}
	type cand struct {
		path    string
		session string
		created time.Time
	}
	var best cand
	for _, e := range entries {
		if !e.IsDir() {
			continue
		}
		sid := e.Name()
		fp := filepath.Join(dir, sid, sid+".jsonl")
		if claimed != nil {
			if _, skip := claimed[fp]; skip {
				continue
			}
		}
		if _, err := os.Stat(fp); err != nil {
			continue
		}
		dirInfo, err := e.Info()
		if err != nil {
			continue
		}
		created := dirInfo.ModTime()
		if created.UnixMilli() < afterMs {
			continue
		}
		if best.path == "" || created.Before(best.created) {
			best = cand{path: fp, session: sid, created: created}
		}
	}
	return best.path, best.session
}

type CursorTranscript struct {
	Path     string
	Session  string
	Created  time.Time
	Modified time.Time
}

func CursorTranscripts(cwd string) []CursorTranscript {
	dir := filepath.Join(CursorProjectDir(cwd), "agent-transcripts")
	entries, err := os.ReadDir(dir)
	if err != nil {
		return nil
	}
	var out []CursorTranscript
	for _, e := range entries {
		if !e.IsDir() {
			continue
		}
		sid := e.Name()
		fp := filepath.Join(dir, sid, sid+".jsonl")
		st, err := os.Stat(fp)
		if err != nil {
			continue
		}
		info, err := e.Info()
		if err != nil {
			continue
		}
		out = append(out, CursorTranscript{Path: fp, Session: sid, Created: info.ModTime(), Modified: st.ModTime()})
	}
	return out
}

func NewerCursorTranscript(all []CursorTranscript, current string, claimed map[string]struct{}) (CursorTranscript, bool) {
	var cur CursorTranscript
	for _, t := range all {
		if t.Path == current {
			cur = t
		}
	}
	if cur.Path == "" {
		return CursorTranscript{}, false
	}
	var best CursorTranscript
	for _, t := range all {
		if t.Path == current {
			continue
		}
		if _, skip := claimed[t.Path]; skip {
			continue
		}
		if !t.Modified.After(cur.Modified) || t.Created.Before(cur.Created) {
			continue
		}
		if best.Path == "" || t.Modified.After(best.Modified) {
			best = t
		}
	}
	return best, best.Path != ""
}

func LatestCursorTranscript(all []CursorTranscript, claimed map[string]struct{}) (CursorTranscript, bool) {
	var best CursorTranscript
	for _, t := range all {
		if _, skip := claimed[t.Path]; skip {
			continue
		}
		if best.Path == "" || t.Modified.After(best.Modified) {
			best = t
		}
	}
	return best, best.Path != ""
}
