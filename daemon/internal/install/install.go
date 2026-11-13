package install

import (
	"io"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
)

func copyBinary(selfPath, dest string, mode os.FileMode) error {
	if err := os.MkdirAll(filepath.Dir(dest), 0o755); err != nil {
		return err
	}
	if sameFile(selfPath, dest) {
		return os.Chmod(dest, mode)
	}
	in, err := os.Open(selfPath)
	if err != nil {
		return err
	}
	defer in.Close()
	tmp := dest + ".tmp"
	out, err := os.OpenFile(tmp, os.O_CREATE|os.O_WRONLY|os.O_TRUNC, mode)
	if err != nil {
		return err
	}
	if _, err := out.ReadFrom(in); err != nil {
		out.Close()
		os.Remove(tmp)
		return err
	}
	if err := out.Close(); err != nil {
		os.Remove(tmp)
		return err
	}
	return os.Rename(tmp, dest)
}

func lingerOff() bool {
	out, err := exec.Command("loginctl", "show-user", os.Getenv("USER"), "-p", "Linger").Output()
	if err != nil {
		return true
	}
	return !strings.Contains(string(out), "Linger=yes")
}

func run(name string, args ...string) {
	cmd := exec.Command(name, args...)
	cmd.Stdout = os.Stderr
	cmd.Stderr = os.Stderr
	_ = cmd.Run()
}

func SelfPath() (string, error) {
	p, err := os.Executable()
	if err != nil {
		return "", err
	}
	return filepath.EvalSymlinks(p)
}

func noopStatus(w io.Writer) {
	_, _ = w.Write([]byte("unknown platform\n"))
}

func sameFile(a, b string) bool {
	sa, err := os.Stat(a)
	if err != nil {
		return false
	}
	sb, err := os.Stat(b)
	if err != nil {
		return false
	}
	return os.SameFile(sa, sb)
}
