package term

import (
	"io"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

func fakeHC(t *testing.T) string {
	t.Helper()
	path := filepath.Join(t.TempDir(), "hc")
	script := "#!/bin/sh\nstty size\necho \"args $*\"\nread line\necho \"got $line\"\n"
	if err := os.WriteFile(path, []byte(script), 0o755); err != nil {
		t.Fatal(err)
	}
	return path
}

func readUntil(t *testing.T, r io.Reader, want string) string {
	t.Helper()
	var all strings.Builder
	buf := make([]byte, 256)
	deadline := time.Now().Add(5 * time.Second)
	for time.Now().Before(deadline) {
		n, err := r.Read(buf)
		all.Write(buf[:n])
		if strings.Contains(all.String(), want) {
			return all.String()
		}
		if err != nil {
			break
		}
	}
	t.Fatalf("never saw %q in %q", want, all.String())
	return ""
}

func TestAttachRunsHCInAPTYWithTheViewerSize(t *testing.T) {
	a, err := Start(fakeHC(t), "t7", 80, 24)
	if err != nil {
		t.Fatal(err)
	}
	defer a.Close()
	out := readUntil(t, a, "args attach --history 1000 t7")
	if !strings.Contains(out, "24 80") {
		t.Fatalf("size missing: %q", out)
	}
	if _, err := a.Write([]byte("hello\r")); err != nil {
		t.Fatal(err)
	}
	readUntil(t, a, "got hello")
}

func TestAttachRejectsSillySizes(t *testing.T) {
	if _, err := Start(fakeHC(t), "t7", 0, 24); err == nil {
		t.Fatal("zero cols accepted")
	}
}
