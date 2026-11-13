//go:build windows

package hcsock_test

import (
	"testing"

	"sh.simple/simpled/internal/hcsock"
)

func TestHerdrSocketWindowsPipeIsWholePath(t *testing.T) {
	t.Setenv("XDG_CONFIG_HOME", "")
	t.Setenv("HERDR_SOCKET_PATH", "")
	t.Setenv("APPDATA", `C:\Users\me\AppData\Roaming`)
	want := `\\.\pipe\C:\Users\me\AppData\Roaming\herdr\herdr.sock`
	if got := hcsock.HerdrSocket(); got != want {
		t.Fatalf("got %q want %q", got, want)
	}
}
