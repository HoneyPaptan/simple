//go:build unix

package servers

import (
	"os"
	"syscall"
)

func KillListed(list []Entry, pid int) error {
	for _, e := range list {
		if e.PID == pid {
			return syscall.Kill(pid, syscall.SIGTERM)
		}
	}
	return os.ErrNotExist
}
