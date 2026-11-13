package appdirs_test

import (
	"path/filepath"
	"testing"

	"sh.simple/simpled/internal/appdirs"
)

func TestLinuxPathsUnchanged(t *testing.T) {
	home := "/home/me"
	t.Setenv("XDG_CONFIG_HOME", "")
	t.Setenv("XDG_STATE_HOME", "")
	t.Setenv("XDG_RUNTIME_DIR", "/run/user/1000")
	if got := appdirs.ConfigDir(home); got != "/home/me/.config/simpled" {
		t.Fatalf("config %q", got)
	}
	if got := appdirs.StateDir(home); got != "/home/me/.local/state/simpled" {
		t.Fatalf("state %q", got)
	}
	if got := appdirs.ControlSocket(home); got != filepath.Join("/run/user/1000", "simpled.sock") {
		t.Fatalf("control %q", got)
	}
}

func TestOpenCodeStateRootFollowsXDGOnEveryOS(t *testing.T) {
	home := filepath.Join("base", "u")
	if got, want := appdirs.OpenCodeStateRoot(home, ""), filepath.Join(home, ".local", "state"); got != want {
		t.Fatalf("state root %q, want %q", got, want)
	}
	custom := filepath.Join("elsewhere", "state")
	if got := appdirs.OpenCodeStateRoot(home, custom); got != custom {
		t.Fatalf("state root %q, want %q", got, custom)
	}
}
