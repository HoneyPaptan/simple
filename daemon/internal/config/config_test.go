package config

import "testing"

func TestHerdrSockIsNotHCSock(t *testing.T) {
	t.Setenv("HERDR_SOCKET_PATH", "")
	t.Setenv("HC_SOCKET", "/run/hc.sock")
	s := NewStore(t.TempDir(), t.TempDir())
	if s.HerdrSocket() == s.HCSock {
		t.Fatalf("herdr socket %q equals hc socket", s.HerdrSocket())
	}
}
