package agents

import (
	"reflect"
	"testing"
)

func TestShellArgv(t *testing.T) {
	env := func(v string) func(string) string { return func(string) string { return v } }
	cases := []struct {
		goos, shell string
		want        []string
	}{
		{"windows", "", []string{"powershell.exe", "-NoLogo"}},
		{"windows", "/usr/bin/fish", []string{"powershell.exe", "-NoLogo"}},
		{"linux", "/usr/bin/fish", []string{"/usr/bin/fish", "-l"}},
		{"linux", "", []string{"/bin/bash", "-l"}},
	}
	for _, c := range cases {
		if got := shellArgv(c.goos, env(c.shell)); !reflect.DeepEqual(got, c.want) {
			t.Errorf("shellArgv(%q, %q) = %v, want %v", c.goos, c.shell, got, c.want)
		}
	}
}
