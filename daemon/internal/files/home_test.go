package files_test

import (
	"os"
	"path/filepath"
	"testing"

	"sh.simple/simpled/internal/files"
)

func TestResolvePathUnderHomeRejectsOutside(t *testing.T) {
	home, err := os.UserHomeDir()
	if err != nil {
		t.Fatal(err)
	}
	outside := filepath.Clean(filepath.Join(home, "..", "outside-simple-search"))
	_, err = files.Search(outside, "x", nil)
	if err == nil || files.ErrKind(err) != "bad_request" {
		t.Fatalf("err %v", err)
	}
	_, err = files.Search(filepath.Join(home, "foo", "..", "..", "etc"), "x", nil)
	if err == nil || files.ErrKind(err) != "bad_request" {
		t.Fatalf("dotdot err %v", err)
	}
}

func TestResolvePathUnderHomeRejectsSymlinkOut(t *testing.T) {
	home := t.TempDir()
	t.Setenv("HOME", home)
	link := filepath.Join(home, "escape")
	if err := os.Symlink(os.TempDir(), link); err != nil {
		t.Skip(err)
	}
	if _, err := files.ResolvePathUnderHome(link); err == nil {
		t.Fatal("symlink out of home accepted")
	}
	inside := filepath.Join(home, "proj")
	if err := os.Mkdir(inside, 0o700); err != nil {
		t.Fatal(err)
	}
	if _, err := files.ResolvePathUnderHome(inside); err != nil {
		t.Fatalf("inside rejected: %v", err)
	}
}
