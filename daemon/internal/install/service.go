package install

import (
	"context"
	"os/exec"
	"strings"
	"time"
)

func systemdServiceComingUp(unit string, run func(string, ...string) (string, error)) bool {
	out, err := run("systemctl", "--user", "show", unit, "-p", "ActiveState", "--value")
	if err != nil && strings.TrimSpace(out) == "" {
		return false
	}
	return systemdComingUp(out)
}

func windowsTaskServiceComingUp(task string, run func(string, ...string) (string, error)) bool {
	out, err := run("schtasks", "/Query", "/TN", task, "/FO", "LIST")
	if err != nil && strings.TrimSpace(out) == "" {
		return false
	}
	return windowsTaskComingUp(out)
}

func systemdComingUp(out string) bool {
	switch strings.TrimSpace(out) {
	case "active", "activating", "reloading":
		return true
	default:
		return false
	}
}

func windowsTaskComingUp(out string) bool {
	for _, line := range strings.Split(out, "\n") {
		rest, ok := strings.CutPrefix(strings.ToLower(strings.TrimSpace(line)), "status:")
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

func commandOutput(name string, args ...string) (string, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()
	out, err := exec.CommandContext(ctx, name, args...).Output()
	return string(out), err
}
