//go:build windows

package term

import ptylib "github.com/aymanbagabas/go-pty"

func hangUp(cmd *ptylib.Cmd) {
	if cmd != nil && cmd.Process != nil {
		_ = cmd.Process.Kill()
	}
}
