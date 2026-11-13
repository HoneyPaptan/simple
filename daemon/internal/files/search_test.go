package files_test

import (
	"os"
	"os/exec"
	"path/filepath"
	"testing"

	"sh.simple/simpled/internal/files"
)

func TestSearchRanking(t *testing.T) {
	home, err := os.UserHomeDir()
	if err != nil {
		t.Fatal(err)
	}
	root := filepath.Join(home, ".simple-test-search")
	_ = os.RemoveAll(root)
	t.Cleanup(func() { os.RemoveAll(root) })
	if err := os.MkdirAll(root, 0o755); err != nil {
		t.Fatal(err)
	}
	for _, rel := range []string{"src/app/Main.kt", "docs/readme.md", "src/util/App.kt"} {
		p := filepath.Join(root, rel)
		if err := os.MkdirAll(filepath.Dir(p), 0o755); err != nil {
			t.Fatal(err)
		}
		if err := os.WriteFile(p, []byte("x"), 0o644); err != nil {
			t.Fatal(err)
		}
	}
	if out, err := exec.Command("git", "-C", root, "init").CombinedOutput(); err != nil {
		t.Fatalf("git init: %s %v", out, err)
	}
	if out, err := exec.Command("git", "-C", root, "add", ".").CombinedOutput(); err != nil {
		t.Fatalf("git add: %s %v", out, err)
	}
	res, err := files.Search(root, "main", nil)
	if err != nil {
		t.Fatal(err)
	}
	if len(res.Paths) < 1 {
		t.Fatalf("%+v", res.Paths)
	}
	if res.Paths[0] != "src/app/Main.kt" {
		t.Fatalf("order %v", res.Paths)
	}
}

func TestSearchRejectsOutsideHome(t *testing.T) {
	_, err := files.Search("/etc", "x", nil)
	if err == nil || files.ErrKind(err) != "bad_request" {
		t.Fatalf("err %v", err)
	}
}
