package inbox

import (
	"encoding/json"
	"os"
	"path/filepath"
	"sync"
	"time"

	"sh.simple/simpled/internal/config"
	"sh.simple/simpled/internal/wire"
)

const maxItems = 500

type Store struct {
	path string
	mu   sync.Mutex
	pub  func(string, any)
	notify func(wire.Item)

	stopped map[string]int64
}

const stopQuiet = 60 * 1000

func (s *Store) MarkStopped(agentID string) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.stopped == nil {
		s.stopped = make(map[string]int64)
	}
	s.stopped[agentID] = NowMs()
}

func (s *Store) takeStoppedLocked(item wire.Item) bool {
	at, ok := s.stopped[item.Agent]
	if !ok || item.Kind != "done" {
		return false
	}
	delete(s.stopped, item.Agent)
	return NowMs()-at <= stopQuiet
}

func New(store *config.Store, publish func(string, any)) *Store {
	return &Store{
		path: filepath.Join(store.StateDir, "inbox.json"),
		pub:  publish,
	}
}

func (s *Store) SetNotify(fn func(wire.Item)) {
	s.mu.Lock()
	s.notify = fn
	s.mu.Unlock()
}

func (s *Store) List() ([]wire.Item, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.loadLocked()
}

func (s *Store) Snapshot() []wire.Item {
	list, _ := s.List()
	return list
}

func (s *Store) Add(item wire.Item) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.takeStoppedLocked(item) {
		return nil
	}
	list, err := s.loadLocked()
	if err != nil {
		return err
	}
	list = append(list, item)
	if len(list) > maxItems {
		list = list[len(list)-maxItems:]
	}
	if err := s.saveLocked(list); err != nil {
		return err
	}
	s.pub("inbox.add", item)
	if s.notify != nil {
		go s.notify(item)
	}
	return nil
}

func (s *Store) Update(item wire.Item) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	list, err := s.loadLocked()
	if err != nil {
		return err
	}
	for i := range list {
		if list[i].ID == item.ID {
			list[i] = item
			if err := s.saveLocked(list); err != nil {
				return err
			}
			s.pub("inbox.update", item)
			return nil
		}
	}
	return os.ErrNotExist
}

func (s *Store) Get(id string) (wire.Item, bool) {
	list, _ := s.List()
	for _, it := range list {
		if it.ID == id {
			return it, true
		}
	}
	return wire.Item{}, false
}

func (s *Store) Answer(id, decision string, option, text *string) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	list, err := s.loadLocked()
	if err != nil {
		return err
	}
	for i := range list {
		if list[i].ID != id {
			continue
		}
		list[i].Resolved = true
		if err := s.saveLocked(list); err != nil {
			return err
		}
		s.pub("inbox.update", list[i])
		return nil
	}
	return os.ErrNotExist
}

func (s *Store) MarkRead(ids []string) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	list, err := s.loadLocked()
	if err != nil {
		return err
	}
	set := map[string]bool{}
	for _, id := range ids {
		set[id] = true
	}
	changed := false
	for i := range list {
		if set[list[i].ID] && !list[i].Read {
			list[i].Read = true
			changed = true
			s.pub("inbox.update", list[i])
		}
	}
	if changed {
		return s.saveLocked(list)
	}
	return nil
}

func (s *Store) ResolveForAgent(agentID string) {
	s.resolveWhere(func(item wire.Item) bool { return item.Agent == agentID })
}

func (s *Store) ResolveMissingAgents(known map[string]bool) {
	s.resolveWhere(func(item wire.Item) bool { return !known[item.Agent] })
}

func (s *Store) resolveWhere(match func(wire.Item) bool) {
	s.mu.Lock()
	defer s.mu.Unlock()
	list, err := s.loadLocked()
	if err != nil {
		return
	}
	changed := false
	for i := range list {
		if match(list[i]) && !list[i].Resolved {
			list[i].Resolved = true
			changed = true
			s.pub("inbox.update", list[i])
		}
	}
	if changed {
		_ = s.saveLocked(list)
	}
}

func (s *Store) Resolve(id string) {
	s.mu.Lock()
	defer s.mu.Unlock()
	list, err := s.loadLocked()
	if err != nil {
		return
	}
	for i := range list {
		if list[i].ID == id && !list[i].Resolved {
			list[i].Resolved = true
			_ = s.saveLocked(list)
			s.pub("inbox.update", list[i])
			return
		}
	}
}

func (s *Store) OpenPromptsForAgent(agentID string) []wire.Item {
	s.mu.Lock()
	defer s.mu.Unlock()
	list, err := s.loadLocked()
	if err != nil {
		return nil
	}
	var out []wire.Item
	for _, it := range list {
		if it.Agent == agentID && it.Kind == "prompt" && !it.Resolved {
			out = append(out, it)
		}
	}
	return out
}

func NewItemID() (string, error) {
	id, err := config.RandomID("i_")
	return id, err
}

func NowMs() int64 {
	return time.Now().UnixMilli()
}

func (s *Store) loadLocked() ([]wire.Item, error) {
	b, err := os.ReadFile(s.path)
	if err != nil {
		if os.IsNotExist(err) {
			return nil, nil
		}
		return nil, err
	}
	var list []wire.Item
	if err := json.Unmarshal(b, &list); err != nil {
		return nil, err
	}
	return list, nil
}

func (s *Store) saveLocked(list []wire.Item) error {
	if err := os.MkdirAll(filepath.Dir(s.path), 0o700); err != nil {
		return err
	}
	b, err := json.MarshalIndent(list, "", "  ")
	if err != nil {
		return err
	}
	return configAtomicWrite(s.path, b)
}

func configAtomicWrite(path string, data []byte) error {
	dir := filepath.Dir(path)
	tmp, err := os.CreateTemp(dir, ".tmp-*")
	if err != nil {
		return err
	}
	name := tmp.Name()
	defer os.Remove(name)
	if _, err := tmp.Write(data); err != nil {
		tmp.Close()
		return err
	}
	if err := tmp.Chmod(0o600); err != nil {
		tmp.Close()
		return err
	}
	if err := tmp.Close(); err != nil {
		return err
	}
	return os.Rename(name, path)
}
