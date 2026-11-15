package install

import (
	"strings"
	"testing"
)

func TestSystemdComingUp(t *testing.T) {
	up := []string{"active", "activating", "reloading", " active\n"}
	for _, s := range up {
		if !systemdComingUp(s) {
			t.Errorf("systemdComingUp(%q) = false", s)
		}
	}
	down := []string{"inactive", "failed", "deactivating", "", "unknown"}
	for _, s := range down {
		if systemdComingUp(s) {
			t.Errorf("systemdComingUp(%q) = true", s)
		}
	}
}

func TestWindowsTaskComingUp(t *testing.T) {
	if !windowsTaskComingUp("TaskName: \\simpled\nStatus: Running\n") {
		t.Fatal("Running")
	}
	if !windowsTaskComingUp("Status:        Queued\n") {
		t.Fatal("Queued")
	}
	if windowsTaskComingUp("Status: Ready\n") {
		t.Fatal("Ready")
	}
	if windowsTaskComingUp("Status: Could not start\n") {
		t.Fatal("Could not start")
	}
}

func TestDarwinComingUp(t *testing.T) {
	if !darwinComingUp("state = running\npid = 42\n") {
		t.Fatal("running")
	}
	if !darwinComingUp("state = spawning\n") {
		t.Fatal("spawning")
	}
	if darwinComingUp("state = not running\n") {
		t.Fatal("not running")
	}
	if !darwinComingUp("4242\t0\tsh.simple.simpled\n") {
		t.Fatal("list pid")
	}
	if darwinComingUp("-\t0\tsh.simple.simpled\n") {
		t.Fatal("list stopped")
	}
}

func TestServiceComingUpLinuxAndWindows(t *testing.T) {
	linux := func(name string, args ...string) (string, error) {
		if name != "systemctl" || !strings.Contains(strings.Join(args, " "), "ActiveState") {
			t.Fatalf("linux command %s %v", name, args)
		}
		return "activating\n", nil
	}
	if !serviceComingUp("linux", 1000, linux) {
		t.Fatal("linux activating")
	}
	stopped := func(string, ...string) (string, error) { return "inactive\n", nil }
	if serviceComingUp("linux", 1000, stopped) {
		t.Fatal("linux inactive")
	}
	windows := func(name string, args ...string) (string, error) {
		joined := strings.Join(args, " ")
		if name != "schtasks" || !strings.Contains(joined, "/TN simpled") {
			t.Fatalf("windows command %s %v", name, args)
		}
		return "Status: Running\n", nil
	}
	if !serviceComingUp("windows", 0, windows) {
		t.Fatal("windows running")
	}
	missing := func(string, ...string) (string, error) { return "", errMissing }
	if serviceComingUp("windows", 0, missing) {
		t.Fatal("windows missing task")
	}
	darwin := func(name string, args ...string) (string, error) {
		if name != "launchctl" {
			t.Fatalf("darwin command %s %v", name, args)
		}
		if len(args) > 0 && args[0] == "print" {
			return "state = spawning\n", nil
		}
		t.Fatalf("darwin should not fall through, args %v", args)
		return "", nil
	}
	if !serviceComingUp("darwin", 501, darwin) {
		t.Fatal("darwin spawning")
	}
}

type missingErr struct{}

func (missingErr) Error() string { return "missing" }

var errMissing = missingErr{}
