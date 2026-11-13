package main

import (
	"errors"
	"path/filepath"
	"strings"
	"testing"
)

func noPath(string) (string, error) { return "", errors.New("missing") }

func envOf(m map[string]string) func(string) string { return func(k string) string { return m[k] } }

func TestFindHCPrefersPath(t *testing.T) {
	got, err := findHC(func(string) (string, error) { return "/usr/bin/hc", nil }, func(string) bool { return false }, "linux", envOf(nil))
	if err != nil || got != "/usr/bin/hc" {
		t.Fatalf("findHC = %q, %v", got, err)
	}
}

func TestFindHCFallsBackToInstallDirs(t *testing.T) {
	cases := []struct {
		goos string
		env  map[string]string
		want string
	}{
		{"linux", map[string]string{"HOME": "/home/u"}, filepath.Join("/home/u", ".local", "bin", "hc")},
		{"linux", map[string]string{"HOME": "/home/u", "HC_INSTALL_DIR": "/opt/hc"}, filepath.Join("/opt/hc", "hc")},
		{"windows", map[string]string{"LOCALAPPDATA": `C:\Users\u\AppData\Local`}, filepath.Join(`C:\Users\u\AppData\Local`, "Programs", "herdr-core", "hc.exe")},
	}
	for _, c := range cases {
		got, err := findHC(noPath, func(p string) bool { return p == c.want }, c.goos, envOf(c.env))
		if err != nil || got != c.want {
			t.Errorf("%s: findHC = %q, %v, want %q", c.goos, got, err, c.want)
		}
	}
}

func TestFindHCMissing(t *testing.T) {
	if _, err := findHC(noPath, func(string) bool { return false }, "linux", envOf(map[string]string{"HOME": "/home/u"})); err == nil {
		t.Fatal("want error when hc is nowhere")
	}
}

func TestHCInstallCommandUsesOfficialScripts(t *testing.T) {
	name, args := hcInstallCommand("windows")
	if name != "powershell.exe" || !strings.Contains(strings.Join(args, " "), "irm "+hcInstallPs1+" | iex") {
		t.Errorf("windows: %s %v", name, args)
	}
	name, args = hcInstallCommand("linux")
	if name != "sh" || args[1] != "curl -fsSL "+hcInstallSh+" | sh" {
		t.Errorf("linux: %s %v", name, args)
	}
}
