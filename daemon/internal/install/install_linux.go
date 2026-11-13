//go:build linux

package install

import (
	"fmt"
	"os"
	"path/filepath"
)

func Install(selfPath string) error {
	home, err := os.UserHomeDir()
	if err != nil {
		return err
	}
	dest := filepath.Join(home, ".local", "bin", "simpled")
	if err := copyBinary(selfPath, dest, 0o755); err != nil {
		return err
	}
	unitPath := filepath.Join(home, ".config", "systemd", "user", "simpled.service")
	unit := fmt.Sprintf(`[Unit]
Description=simpled host daemon
After=network.target

[Service]
ExecStart=%s serve
Restart=always

[Install]
WantedBy=default.target
`, dest)
	if err := os.MkdirAll(filepath.Dir(unitPath), 0o755); err != nil {
		return err
	}
	if err := os.WriteFile(unitPath, []byte(unit), 0o644); err != nil {
		return err
	}
	run("systemctl", "--user", "daemon-reload")
	run("systemctl", "--user", "enable", "simpled.service")
	run("systemctl", "--user", "restart", "simpled.service")
	if lingerOff() {
		fmt.Println("loginctl enable-linger $USER")
	}
	return InstallHooks(dest)
}

func Uninstall() error {
	run("systemctl", "--user", "disable", "--now", "simpled.service")
	home, _ := os.UserHomeDir()
	if home != "" {
		_ = os.Remove(filepath.Join(home, ".config", "systemd", "user", "simpled.service"))
	}
	run("systemctl", "--user", "daemon-reload")
	dest := filepath.Join(home, ".local", "bin", "simpled")
	_ = os.Remove(dest)
	return UninstallHooks()
}

func LingerEnabled() bool {
	return !lingerOff()
}


