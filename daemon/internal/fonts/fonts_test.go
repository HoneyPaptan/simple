package fonts_test

import (
	"encoding/binary"
	"os"
	"path/filepath"
	"testing"

	"sh.simple/simpled/internal/fonts"
	"sh.simple/simpled/internal/wire"
)

func TestAllowedPath(t *testing.T) {
	list := []wire.FontEntry{{Path: "/tmp/a.ttf", Family: "a"}}
	if !fonts.Allowed(list, "/tmp/a.ttf") {
		t.Fatal("allowed")
	}
	if fonts.Allowed(list, "/tmp/other.ttf") {
		t.Fatal("deny")
	}
}

func writeFont(t *testing.T, dir, name string, fixed uint32) string {
	t.Helper()
	post := make([]byte, 32)
	binary.BigEndian.PutUint32(post[0:4], 0x00030000)
	binary.BigEndian.PutUint32(post[12:16], fixed)
	head := make([]byte, 12)
	binary.BigEndian.PutUint32(head[0:4], 0x00010000)
	binary.BigEndian.PutUint16(head[4:6], 1)
	rec := make([]byte, 16)
	copy(rec[0:4], "post")
	binary.BigEndian.PutUint32(rec[8:12], uint32(len(head)+len(rec)))
	binary.BigEndian.PutUint32(rec[12:16], uint32(len(post)))
	data := append(append(head, rec...), post...)
	path := filepath.Join(dir, name)
	if err := os.WriteFile(path, data, 0o644); err != nil {
		t.Fatal(err)
	}
	return path
}

func TestListOnlyMonospace(t *testing.T) {
	home := t.TempDir()
	dir := filepath.Join(home, ".fonts")
	if err := os.MkdirAll(dir, 0o755); err != nil {
		t.Fatal(err)
	}
	mono := writeFont(t, dir, "Mono.ttf", 1)
	writeFont(t, dir, "Serif.otf", 0)
	if err := os.WriteFile(filepath.Join(dir, "Broken.ttf"), []byte("nope"), 0o644); err != nil {
		t.Fatal(err)
	}
	list, err := fonts.List(home)
	if err != nil {
		t.Fatal(err)
	}
	for _, e := range list {
		if filepath.Dir(e.Path) != dir {
			continue
		}
		if e.Path != mono {
			t.Fatalf("listed a font that is not fixed pitch: %s", e.Path)
		}
	}
	if !fonts.Allowed(list, mono) {
		t.Fatal("monospace font missing from the list")
	}
}
