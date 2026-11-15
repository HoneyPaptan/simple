//go:build darwin

package install

import (
	"fmt"
	"os"
	"path/filepath"
)

const launchLabel = "sh.simple.simpled"

func Install(selfPath string) error {
	home, err := os.UserHomeDir()
	if err != nil {
		return err
	}
	dest := filepath.Join(home, "Library", "Application Support", "simpled", "simpled")
	if err := copyBinary(selfPath, dest, 0o755); err != nil {
		return err
	}
	logDir := filepath.Join(home, "Library", "Logs", "simpled")
	if err := os.MkdirAll(logDir, 0o755); err != nil {
		return err
	}
	plist := filepath.Join(home, "Library", "LaunchAgents", launchLabel+".plist")
	body := fmt.Sprintf(`<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>Label</key>
  <string>%s</string>
  <key>ProgramArguments</key>
  <array>
    <string>%s</string>
    <string>serve</string>
  </array>
  <key>RunAtLoad</key>
  <true/>
  <key>KeepAlive</key>
  <true/>
  <key>StandardOutPath</key>
  <string>%s/simpled.log</string>
  <key>StandardErrorPath</key>
  <string>%s/simpled.log</string>
</dict>
</plist>
`, launchLabel, dest, logDir, logDir)
	if err := os.MkdirAll(filepath.Dir(plist), 0o755); err != nil {
		return err
	}
	if err := os.WriteFile(plist, []byte(body), 0o644); err != nil {
		return err
	}
	uid := os.Getuid()
	run("launchctl", "bootout", fmt.Sprintf("gui/%d", uid), plist)
	run("launchctl", "bootstrap", fmt.Sprintf("gui/%d", uid), plist)
	return InstallHooks(dest)
}

func Uninstall() error {
	home, _ := os.UserHomeDir()
	plist := filepath.Join(home, "Library", "LaunchAgents", launchLabel+".plist")
	uid := os.Getuid()
	run("launchctl", "bootout", fmt.Sprintf("gui/%d", uid), plist)
	_ = os.Remove(plist)
	dest := filepath.Join(home, "Library", "Application Support", "simpled", "simpled")
	_ = os.Remove(dest)
	return UninstallHooks()
}

func LingerEnabled() bool {
	return true
}

func ServiceComingUp() bool {
	return false
}
