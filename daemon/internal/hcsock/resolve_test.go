package hcsock_test

import (
	"fmt"
	"os"
	"path/filepath"
	"testing"

	"sh.simple/simpled/internal/hcsock"
)

func TestResolveHCEnv(t *testing.T) {
	t.Setenv("HC_SOCKET", "/tmp/custom.sock")
	t.Setenv("HC_SESSION", "work")
	got := hcsock.Resolve()
	if got != "/tmp/custom.sock" {
		t.Fatalf("got %q", got)
	}
}

func TestResolveFromConfig(t *testing.T) {
	dir := t.TempDir()
	cfg := filepath.Join(dir, "herdr-core", "config.toml")
	if err := os.MkdirAll(filepath.Dir(cfg), 0o700); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(cfg, []byte("socket = \"/from/config.sock\"\n"), 0o600); err != nil {
		t.Fatal(err)
	}
	t.Setenv("HC_SOCKET", "")
	t.Setenv("XDG_CONFIG_HOME", dir)
	t.Setenv("HC_SESSION", "default")
	got := hcsock.Resolve()
	if got != "/from/config.sock" {
		t.Fatalf("got %q", got)
	}
}

func TestDefaultLinuxRuntime(t *testing.T) {
	rt := t.TempDir()
	t.Setenv("HC_SOCKET", "")
	t.Setenv("XDG_CONFIG_HOME", t.TempDir())
	t.Setenv("XDG_RUNTIME_DIR", rt)
	t.Setenv("HC_SESSION", "s1")
	got := hcsock.Resolve()
	want := filepath.Join(rt, "herdr-core", "s1.sock")
	if got != want {
		t.Fatalf("got %q want %q", got, want)
	}
}

func TestDefaultLinuxStateFallback(t *testing.T) {
	fallback := filepath.Join("/run/user", fmt.Sprint(os.Getuid()), "herdr-core")
	if st, err := os.Stat(fallback); err == nil && st.IsDir() {
		t.Skip("herdr-core runtime dir present")
	}
	state := t.TempDir()
	t.Setenv("HC_SOCKET", "")
	t.Setenv("XDG_CONFIG_HOME", t.TempDir())
	t.Setenv("XDG_RUNTIME_DIR", "")
	t.Setenv("XDG_STATE_HOME", state)
	t.Setenv("HC_SESSION", "dev")
	got := hcsock.Resolve()
	want := filepath.Join(state, "herdr-core", "dev", "dev.sock")
	if got != want {
		t.Fatalf("got %q want %q", got, want)
	}
}

func TestWindowsPipeName(t *testing.T) {
	got := hcsock.WindowsDefaultPipe("alice", "sess")
	want := `\\.\pipe\herdr-core-alice-sess.sock`
	if got != want {
		t.Fatalf("got %q want %q", got, want)
	}
	got = hcsock.WindowsDefaultPipe("a.lice", "sess")
	if got != want {
		t.Fatalf("sanitized got %q", got)
	}
}

func TestHerdrSocketIsNotTheHCSocket(t *testing.T) {
	home := t.TempDir()
	t.Setenv("HOME", home)
	t.Setenv("XDG_CONFIG_HOME", "")
	t.Setenv("HERDR_SOCKET_PATH", "")
	t.Setenv("HC_SOCKET", "/run/hc.sock")
	want := filepath.Join(home, ".config", "herdr", "herdr.sock")
	if got := hcsock.HerdrSocket(); got != want {
		t.Fatalf("got %q want %q", got, want)
	}
}

func TestHerdrSocketOverrides(t *testing.T) {
	t.Setenv("XDG_CONFIG_HOME", "/cfg")
	t.Setenv("HERDR_SOCKET_PATH", "")
	if got := hcsock.HerdrSocket(); got != filepath.Join("/cfg", "herdr", "herdr.sock") {
		t.Fatalf("xdg %q", got)
	}
	t.Setenv("HERDR_SOCKET_PATH", "/custom/herdr.sock")
	if got := hcsock.HerdrSocket(); got != "/custom/herdr.sock" {
		t.Fatalf("env %q", got)
	}
}

func TestConfigSocketKeyIsExact(t *testing.T) {
	dir := t.TempDir()
	cfg := filepath.Join(dir, "herdr-core", "config.toml")
	if err := os.MkdirAll(filepath.Dir(cfg), 0o700); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(cfg, []byte("socket_timeout = 5\n"), 0o600); err != nil {
		t.Fatal(err)
	}
	t.Setenv("HC_SOCKET", "")
	t.Setenv("XDG_CONFIG_HOME", dir)
	t.Setenv("XDG_RUNTIME_DIR", "/rt")
	t.Setenv("HC_SESSION", "")
	if got := hcsock.Resolve(); got != filepath.Join("/rt", "herdr-core", "default.sock") {
		t.Fatalf("got %q", got)
	}
}
