package term

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestHerdrAttachHarnessBusyThenDeliversScreen(t *testing.T) {
	dir := t.TempDir()
	flag := filepath.Join(dir, "once")
	script := "#!/bin/sh\n" +
		"case \"$*\" in\n" +
		"  *attach*)\n" +
		"    if [ -f \"" + flag + "\" ]; then rm -f \"" + flag + "\"; printf screen; else touch \"" + flag + "\"; printf \"read in progress\\n\"; fi\n" +
		"    while read line; do :; done ;;\n" +
		"esac\n"
	path := filepath.Join(dir, "herdr")
	if err := os.WriteFile(path, []byte(script), 0o755); err != nil {
		t.Fatal(err)
	}
	prev := HerdrBin
	HerdrBin = path
	t.Cleanup(func() { HerdrBin = prev })

	a, err := StartHerdr("claude:p1", 80, 24)
	if err != nil {
		t.Fatal(err)
	}
	defer a.Close()
	out := readUntil(t, a, "screen")
	if strings.Contains(out, "read in progress") {
		t.Fatalf("stale busy text in output %q", out)
	}
}
