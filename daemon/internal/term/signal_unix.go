//go:build unix

package term

import (
	"syscall"

	ptylib "github.com/aymanbagabas/go-pty"
)

func hangUp(cmd *ptylib.Cmd) {
	if cmd != nil && cmd.Process != nil {
		_ = cmd.Process.Signal(syscall.SIGHUP)
	}
}
