//go:build windows

package install

import (
	"strings"
	"testing"
)

func TestHookShellUsesForwardSlashes(t *testing.T) {
	cmd := hookShell(`C:\Users\me\simpled.exe`, "cursor", "stop")
	if strings.Contains(cmd, `\`) {
		t.Fatalf("backslashes in hook command: %q", cmd)
	}
	if !strings.Contains(cmd, `"C:/Users/me/simpled.exe"`) {
		t.Fatalf("quoted path missing: %q", cmd)
	}
}
