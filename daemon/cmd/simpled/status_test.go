package main

import (
	"net"
	"path/filepath"
	"testing"
	"time"
)

func TestStatusWaitsOutTheStartupRace(t *testing.T) {
	sock := filepath.Join(t.TempDir(), "simpled.sock")
	stop := make(chan struct{})
	ready := make(chan struct{})
	go func() {
		time.Sleep(900 * time.Millisecond)
		ln, err := net.Listen("unix", sock)
		if err != nil {
			return
		}
		defer ln.Close()
		close(ready)
		<-stop
	}()
	t.Cleanup(func() { close(stop) })
	start := time.Now()
	got := observeDaemon(sock, func() bool { return true }, statusStartWait)
	if got != statusRunning {
		t.Fatalf("observeDaemon = %s, want running (socket was down for about a second while the service was up)", got)
	}
	if time.Since(start) < 800*time.Millisecond {
		t.Fatal("status returned before the daemon listened")
	}
	select {
	case <-ready:
	default:
		t.Fatal("listener was not up")
	}
}

func TestStatusSaysStartingNotDownWhileServiceIsUp(t *testing.T) {
	sock := filepath.Join(t.TempDir(), "missing.sock")
	got := observeDaemon(sock, func() bool { return true }, 40*time.Millisecond)
	if got != statusStarting {
		t.Fatalf("observeDaemon = %s, want starting", got)
	}
}

func TestStatusDownWhenServiceIsStopped(t *testing.T) {
	sock := filepath.Join(t.TempDir(), "missing.sock")
	start := time.Now()
	got := observeDaemon(sock, func() bool { return false }, statusStartWait)
	if got != statusDown {
		t.Fatalf("observeDaemon = %s, want down", got)
	}
	if time.Since(start) > 200*time.Millisecond {
		t.Fatalf("waited %s for a stopped service", time.Since(start))
	}
}
