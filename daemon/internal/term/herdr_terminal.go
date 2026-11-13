package term

import (
	"bytes"
	"errors"
	"os"
	"time"
)

func StartHerdrTerminal(terminalID string, cols, rows int) (*Attach, error) {
	var lastErr error
	for try := 0; try < herdrAttachTries; try++ {
		a, busy, err := startHerdrTerminalOnce(terminalID, cols, rows)
		if err != nil {
			return nil, err
		}
		if !busy {
			return a, nil
		}
		a.Close()
		lastErr = errors.New("herdr terminal busy")
		time.Sleep(time.Duration(try+1) * 150 * time.Millisecond)
	}
	return nil, lastErr
}

func startHerdrTerminalOnce(terminalID string, cols, rows int) (*Attach, bool, error) {
	cols, rows, err := clampSize(cols, rows)
	if err != nil {
		return nil, false, err
	}
	bin, err := herdrBin()
	if err != nil {
		return nil, false, err
	}
	env := append(os.Environ(), "TERM=xterm-256color", "COLORTERM=truecolor")
	master, cmd, p, err := startPTY(bin, herdrTerminalAttachArgs(terminalID), env, cols, rows)
	if err != nil {
		return nil, false, err
	}
	a := &Attach{master: master, pty: p, cmd: cmd, out: make(chan []byte, 64), done: make(chan struct{})}
	go pumpMaster(master, a.out, a.done)
	a.pending = collectEarly(a.out, 300*time.Millisecond)
	return a, bytes.Contains(a.pending, herdrAttachBusy), nil
}
