package binpath

import (
	"os"
	"path/filepath"
	"testing"
)

func TestFindFallsBackToUserBinDirs(t *testing.T) {
	home := t.TempDir()
	t.Setenv("HOME", home)
	t.Setenv("PATH", "/nonexistent")
	bin := filepath.Join(home, ".local", "bin", "hc")
	if err := os.MkdirAll(filepath.Dir(bin), 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(bin, []byte("#!/bin/sh\n"), 0o755); err != nil {
		t.Fatal(err)
	}
	got, err := Find("hc")
	if err != nil || got != bin {
		t.Fatalf("got %q %v", got, err)
	}
	if _, err := Find("missing-tool"); err == nil {
		t.Fatal("missing tool found")
	}
}
