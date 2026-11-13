package binpath

import (
	"errors"
	"os"
	"os/exec"
	"path/filepath"
)

var userDirs = []string{".local/bin", "bin", ".cargo/bin", "go/bin", ".bun/bin", ".npm-global/bin", ".opencode/bin"}

func Find(name string) (string, error) {
	if p, err := exec.LookPath(name); err == nil {
		return p, nil
	}
	home, err := os.UserHomeDir()
	if err != nil {
		return "", err
	}
	for _, dir := range userDirs {
		p := filepath.Join(home, dir, name)
		if isExecutable(p) {
			return p, nil
		}
	}
	return "", errors.New(name + " not found")
}

func isExecutable(p string) bool {
	st, err := os.Stat(p)
	return err == nil && !st.IsDir() && st.Mode()&0o111 != 0
}
