//go:build windows

package servers

import (
	"os"
)

func KillListed(list []Entry, pid int) error {
	for _, e := range list {
		if e.PID == pid {
			proc, err := os.FindProcess(pid)
			if err != nil {
				return err
			}
			return proc.Kill()
		}
	}
	return os.ErrNotExist
}
