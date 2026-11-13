package term

import (
	"bytes"
	"errors"
	"io"
	"os"
	"time"

	"sh.simple/simpled/internal/binpath"
)

const herdrAttachTries = 5

var herdrAttachBusy = []byte("read in progress")

var HerdrBin string

func herdrBin() (string, error) {
	if HerdrBin != "" {
		return HerdrBin, nil
	}
	return binpath.Find("herdr")
}

func StartHerdr(target string, cols, rows int) (*Attach, error) {
	var lastErr error
	for try := 0; try < herdrAttachTries; try++ {
		a, busy, err := startHerdrOnce(target, cols, rows)
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

func startHerdrOnce(target string, cols, rows int) (*Attach, bool, error) {
	cols, rows, err := clampSize(cols, rows)
	if err != nil {
		return nil, false, err
	}
	bin, err := herdrBin()
	if err != nil {
		return nil, false, err
	}
	env := append(os.Environ(), "TERM=xterm-256color", "COLORTERM=truecolor")
	master, cmd, p, err := startPTY(bin, herdrAgentAttachArgs(target), env, cols, rows)
	if err != nil {
		return nil, false, err
	}
	a := &Attach{master: master, pty: p, cmd: cmd, out: make(chan []byte, 64), done: make(chan struct{})}
	go pumpMaster(master, a.out, a.done)
	a.pending = collectEarly(a.out, 300*time.Millisecond)
	return a, bytes.Contains(a.pending, herdrAttachBusy), nil
}

func pumpMaster(master io.Reader, out chan<- []byte, done <-chan struct{}) {
	defer close(out)
	for {
		buf := make([]byte, 32*1024)
		n, err := master.Read(buf)
		if n > 0 {
			select {
			case out <- buf[:n]:
			case <-done:
				return
			}
		}
		if err != nil {
			return
		}
	}
}

func collectEarly(out <-chan []byte, window time.Duration) []byte {
	var early []byte
	deadline := time.After(window)
	for {
		select {
		case chunk, ok := <-out:
			if !ok {
				return early
			}
			early = append(early, chunk...)
		case <-deadline:
			return early
		}
	}
}
