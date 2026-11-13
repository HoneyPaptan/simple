package pair

import (
	"crypto/rand"
	"encoding/base64"
	"sync"
	"time"
)

type Manager struct {
	mu          sync.Mutex
	code        string
	expires     time.Time
	used        bool
	wrong       int
	lockedUntil time.Time
}

func NewManager() *Manager {
	return &Manager{}
}

func (m *Manager) NewCode() (string, time.Time) {
	m.mu.Lock()
	defer m.mu.Unlock()
	var b [16]byte
	rand.Read(b[:])
	m.code = base64.RawURLEncoding.EncodeToString(b[:])
	m.expires = time.Now().Add(5 * time.Minute)
	m.used = false
	return m.code, m.expires
}

func (m *Manager) CurrentCode() (string, bool) {
	m.mu.Lock()
	defer m.mu.Unlock()
	if m.code == "" || time.Now().After(m.expires) || m.used {
		return "", false
	}
	return m.code, true
}

func (m *Manager) Validate(guess string) (ok bool, kind string) {
	m.mu.Lock()
	defer m.mu.Unlock()
	if time.Now().Before(m.lockedUntil) {
		return false, "bad_code"
	}
	if m.used {
		return false, "used"
	}
	if m.code == "" || time.Now().After(m.expires) {
		m.wrong++
		if m.wrong >= 5 {
			m.lockedUntil = time.Now().Add(time.Minute)
			m.wrong = 0
		}
		return false, "bad_code"
	}
	if guess != m.code {
		m.wrong++
		if m.wrong >= 5 {
			m.lockedUntil = time.Now().Add(time.Minute)
			m.wrong = 0
		}
		return false, "bad_code"
	}
	m.used = true
	return true, ""
}

func (m *Manager) Consume() {
	m.mu.Lock()
	m.used = true
	m.mu.Unlock()
}
