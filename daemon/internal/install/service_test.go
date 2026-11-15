package install

import (
	"strings"
	"testing"
)

func TestSystemdComingUp(t *testing.T) {
	for _, s := range []string{"active", "activating", "reloading", " active\n"} {
		if !systemdComingUp(s) {
			t.Errorf("systemdComingUp(%q) = false", s)
		}
	}
	for _, s := range []string{"inactive", "failed", "deactivating", "", "unknown"} {
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
	if windowsTaskComingUp("Status: Ready\n") || windowsTaskComingUp("Status: Could not start\n") {
		t.Fatal("not running")
	}
}

func TestServiceQueryUsesUnitAndTaskNames(t *testing.T) {
	var got []string
	run := func(name string, args ...string) (string, error) {
		got = append(got, name+" "+strings.Join(args, " "))
		if name == "systemctl" {
			return "activating\n", nil
		}
		return "Status: Running\n", nil
	}
	if !systemdServiceComingUp("simpled.service", run) {
		t.Fatal("linux activating")
	}
	if !windowsTaskServiceComingUp("simpled", run) {
		t.Fatal("windows running")
	}
	if len(got) != 2 || !strings.Contains(got[0], "simpled.service") || !strings.Contains(got[1], "/TN simpled") {
		t.Fatalf("queries = %v", got)
	}
	down := func(string, ...string) (string, error) { return "inactive\n", nil }
	if systemdServiceComingUp("simpled.service", down) {
		t.Fatal("linux inactive")
	}
	missing := func(string, ...string) (string, error) { return "", errMissing }
	if windowsTaskServiceComingUp("simpled", missing) {
		t.Fatal("windows missing task")
	}
}

type missingErr struct{}

func (missingErr) Error() string { return "missing" }

var errMissing = missingErr{}
