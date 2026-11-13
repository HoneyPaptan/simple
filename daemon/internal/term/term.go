package term

import (
	"errors"
	"io"
	"os"
	"strconv"
	"sync"

	ptylib "github.com/aymanbagabas/go-pty"
)

const (
	historyLines = 1000
	maxCells     = 1000
)

type Attach struct {
	master  io.ReadWriteCloser
	pty     ptylib.Pty
	cmd     *ptylib.Cmd
	once    sync.Once
	out     chan []byte
	done    chan struct{}
	pending []byte
}

func clampSize(cols, rows int) (int, int, error) {
	if cols < 2 || rows < 2 || cols > maxCells || rows > maxCells {
		return 0, 0, errors.New("bad size")
	}
	return cols, rows, nil
}

func Start(hcBin, terminal string, cols, rows int) (*Attach, error) {
	cols, rows, err := clampSize(cols, rows)
	if err != nil {
		return nil, err
	}
	env := append(os.Environ(), "TERM=xterm-256color", "COLORTERM=truecolor")
	master, cmd, p, err := startPTY(hcBin, []string{"attach", "--history", strconv.Itoa(historyLines), terminal}, env, cols, rows)
	if err != nil {
		return nil, err
	}
	return &Attach{master: master, pty: p, cmd: cmd}, nil
}

func (a *Attach) Read(p []byte) (int, error) {
	if a.out == nil {
		return a.master.Read(p)
	}
	if len(a.pending) == 0 {
		chunk, ok := <-a.out
		if !ok {
			return 0, io.EOF
		}
		a.pending = chunk
	}
	n := copy(p, a.pending)
	a.pending = a.pending[n:]
	return n, nil
}

func (a *Attach) Write(p []byte) (int, error) { return a.master.Write(p) }

func (a *Attach) Resize(cols, rows int) error {
	cols, rows, err := clampSize(cols, rows)
	if err != nil {
		return err
	}
	return a.pty.Resize(cols, rows)
}

func (a *Attach) Close() {
	a.once.Do(func() {
		if a.done != nil {
			close(a.done)
		}
		hangUp(a.cmd)
		a.master.Close()
		a.pty.Close()
		go a.cmd.Wait()
	})
}
