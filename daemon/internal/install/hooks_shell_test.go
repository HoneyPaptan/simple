package install

import (
	"strings"
	"testing"
)

func TestHookShellShapeUnix(t *testing.T) {
	cmd := hookShell("/usr/bin/simpled", "cursor", "sessionStart")
	if !strings.Contains(cmd, `'`) {
		t.Fatalf("unix quote missing: %q", cmd)
	}
	if !strings.Contains(cmd, "hook cursor sessionStart") {
		t.Fatal(cmd)
	}
}

