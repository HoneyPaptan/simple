package install

import (
	"context"
	"fmt"
	"os"
	"os/exec"
	"runtime"
	"strconv"
	"strings"
	"time"
)

func ServiceComingUp() bool {
	return serviceComingUp(runtime.GOOS, os.Getuid(), commandOutput)
}

func serviceComingUp(goos string, uid int, run func(string, ...string) (string, error)) bool {
	switch goos {
	case "windows":
		out, err := run("schtasks", "/Query", "/TN", "simpled", "/FO", "LIST")
		if err != nil && strings.TrimSpace(out) == "" {
			return false
		}
		return windowsTaskComingUp(out)
	case "darwin":
		target := fmt.Sprintf("gui/%d/sh.simple.simpled", uid)
		out, err := run("launchctl", "print", target)
		if err == nil && darwinComingUp(out) {
			return true
		}
		listed, err2 := run("launchctl", "list", "sh.simple.simpled")
		if err2 != nil && strings.TrimSpace(listed) == "" {
			return false
		}
		return darwinComingUp(listed)
	default:
		out, err := run("systemctl", "--user", "show", "simpled.service", "-p", "ActiveState", "--value")
		if err != nil && strings.TrimSpace(out) == "" {
			return false
		}
		return systemdComingUp(out)
	}
}

func systemdComingUp(out string) bool {
	state := strings.TrimSpace(out)
	switch state {
	case "active", "activating", "reloading":
		return true
	default:
		return false
	}
}

func windowsTaskComingUp(out string) bool {
	for _, line := range strings.Split(out, "\n") {
		line = strings.TrimSpace(line)
		rest, ok := strings.CutPrefix(strings.ToLower(line), "status:")
		if !ok {
			continue
		}
		switch strings.TrimSpace(rest) {
		case "running", "queued":
			return true
		}
	}
	return false
}

func darwinComingUp(out string) bool {
	sawState := false
	for _, line := range strings.Split(out, "\n") {
		line = strings.TrimSpace(line)
		rest, ok := strings.CutPrefix(line, "state =")
		if !ok {
			continue
		}
		sawState = true
		switch strings.TrimSpace(rest) {
		case "running", "spawning", "spawn scheduled", "xpcproxy":
			return true
		}
	}
	if sawState {
		return false
	}
	for _, line := range strings.Split(out, "\n") {
		fields := strings.Fields(line)
		if len(fields) == 0 {
			continue
		}
		pid, err := strconv.Atoi(fields[0])
		if err == nil && pid > 0 {
			return true
		}
	}
	return false
}

func commandOutput(name string, args ...string) (string, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()
	out, err := exec.CommandContext(ctx, name, args...).Output()
	return string(out), err
}
