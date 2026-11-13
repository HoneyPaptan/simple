package agents

import (
	"encoding/json"
	"maps"
	"os"
	"path/filepath"
)

type herdrSaved struct {
	Session   *string `json:"session,omitempty"`
	StartedMs int64   `json:"started_ms"`
	Status    string  `json:"status,omitempty"`
	UpdatedMs int64   `json:"updated_ms,omitempty"`
}

func (s *Store) PersistHerdr(path string) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.herdrSavedPath = path
	raw, err := os.ReadFile(path)
	if err != nil {
		return
	}
	var saved map[string]herdrSaved
	if json.Unmarshal(raw, &saved) == nil {
		s.herdrSaved = saved
	}
}

func (s *Store) saveHerdrLocked() {
	if s.herdrSavedPath == "" {
		return
	}
	next := make(map[string]herdrSaved)
	for id, a := range s.byID {
		if a.Runtime == "herdr" {
			next[id] = herdrSaved{Session: a.Session, StartedMs: a.StartedMs, Status: a.Status, UpdatedMs: a.UpdatedMs}
		}
	}
	if maps.EqualFunc(next, s.herdrSaved, sameHerdrSaved) {
		return
	}
	s.herdrSaved = next
	raw, err := json.Marshal(next)
	if err != nil {
		return
	}
	tmp := s.herdrSavedPath + ".tmp"
	if os.MkdirAll(filepath.Dir(tmp), 0o700) != nil || os.WriteFile(tmp, raw, 0o600) != nil {
		return
	}
	_ = os.Rename(tmp, s.herdrSavedPath)
}

func sameHerdrSaved(a, b herdrSaved) bool {
	if a.StartedMs != b.StartedMs || a.Status != b.Status || a.UpdatedMs != b.UpdatedMs || (a.Session == nil) != (b.Session == nil) {
		return false
	}
	return a.Session == nil || *a.Session == *b.Session
}
