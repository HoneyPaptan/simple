package control

import (
	"errors"
	"net"
	"os"
	"path/filepath"
	"testing"

	"sh.simple/simpled/internal/config"
)

func shortSock(t *testing.T) string {
	t.Helper()
	dir, err := os.MkdirTemp("", "sc")
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { os.RemoveAll(dir) })
	return filepath.Join(dir, "c.sock")
}

func TestListenRefusesToStealLiveSocket(t *testing.T) {
	sock := shortSock(t)
	live, err := net.Listen("unix", sock)
	if err != nil {
		t.Fatal(err)
	}
	defer live.Close()
	s := &Server{store: &config.Store{ControlSock: sock}}
	if err := s.Listen(); !errors.Is(err, ErrRunning) {
		t.Fatalf("Listen on a live socket = %v, want ErrRunning", err)
	}
	if !Alive(sock) {
		t.Fatal("live socket was removed by the second Listen")
	}
}

func TestListenReplacesStaleSocket(t *testing.T) {
	sock := shortSock(t)
	if err := os.WriteFile(sock, nil, 0o600); err != nil {
		t.Fatal(err)
	}
	s := &Server{store: &config.Store{ControlSock: sock}}
	if err := s.Listen(); err != nil {
		t.Fatalf("Listen over a stale file = %v", err)
	}
	defer s.Close()
	if !Alive(sock) {
		t.Fatal("new socket is not accepting")
	}
}
