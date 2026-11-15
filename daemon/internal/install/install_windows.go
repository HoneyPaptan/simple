//go:build windows

package install

import (
	"fmt"
	"os"
	"path/filepath"

	"sh.simple/simpled/internal/appdirs"
)

const taskName = "simpled"

func Install(selfPath string) error {
	home, err := os.UserHomeDir()
	if err != nil {
		return err
	}
	binDir := filepath.Join(appdirs.StateDir(home), "bin")
	dest := filepath.Join(binDir, "simpled.exe")
	if !sameFile(selfPath, dest) {
		run("schtasks", "/End", "/TN", taskName)
	}
	if err := copyBinary(selfPath, dest, 0o755); err != nil {
		return err
	}
	if err := createScheduledTask(dest); err != nil {
		return err
	}
	if err := InstallHooks(dest); err != nil {
		return err
	}
	run("schtasks", "/End", "/TN", taskName)
	run("schtasks", "/Run", "/TN", taskName)
	return nil
}

func Uninstall() error {
	run("schtasks", "/Delete", "/F", "/TN", taskName)
	home, _ := os.UserHomeDir()
	dest := filepath.Join(appdirs.StateDir(home), "bin", "simpled.exe")
	_ = os.Remove(dest)
	return UninstallHooks()
}

func LingerEnabled() bool {
	return true
}

func ServiceComingUp() bool {
	return windowsTaskServiceComingUp(taskName, commandOutput)
}

func taskXML(exe string) string {
	return fmt.Sprintf(`<?xml version="1.0" encoding="UTF-16"?>
<Task version="1.2" xmlns="http://schemas.microsoft.com/windows/2004/02/mit/task">
  <Triggers>
    <LogonTrigger><Enabled>true</Enabled></LogonTrigger>
  </Triggers>
  <Principals>
    <Principal id="Author"><LogonType>InteractiveToken</LogonType><RunLevel>LeastPrivilege</RunLevel></Principal>
  </Principals>
  <Settings>
    <MultipleInstancesPolicy>IgnoreNew</MultipleInstancesPolicy>
    <RestartOnFailure><Interval>PT1M</Interval><Count>999</Count></RestartOnFailure>
    <Enabled>true</Enabled>
  </Settings>
  <Actions>
    <Exec><Command>%s</Command><Arguments>serve</Arguments></Exec>
  </Actions>
</Task>`, exe)
}

func createScheduledTask(exe string) error {
	f, err := os.CreateTemp("", "simpled-task-*.xml")
	if err != nil {
		return err
	}
	defer os.Remove(f.Name())
	if _, err := f.WriteString(taskXML(exe)); err != nil {
		f.Close()
		return err
	}
	if err := f.Close(); err != nil {
		return err
	}
	run("schtasks", "/Create", "/F", "/TN", taskName, "/XML", f.Name())
	return nil
}
