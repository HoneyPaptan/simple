package term

import (
	"io"

	ptylib "github.com/aymanbagabas/go-pty"
)

func startPTY(bin string, args, env []string, cols, rows int) (io.ReadWriteCloser, *ptylib.Cmd, ptylib.Pty, error) {
	p, err := ptylib.New()
	if err != nil {
		return nil, nil, nil, err
	}
	if err := p.Resize(cols, rows); err != nil {
		p.Close()
		return nil, nil, nil, err
	}
	cmd := p.Command(bin, args...)
	cmd.Env = env
	if err := cmd.Start(); err != nil {
		p.Close()
		return nil, nil, nil, err
	}
	if u, ok := p.(ptylib.UnixPty); ok {
		_ = u.Slave().Close()
	}
	return ptyMaster(p), cmd, p, nil
}

func ptyMaster(p ptylib.Pty) io.ReadWriteCloser {
	if u, ok := p.(ptylib.UnixPty); ok {
		return u.Master()
	}
	return p
}
