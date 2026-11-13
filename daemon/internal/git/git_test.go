package git_test

import (
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"testing"

	"sh.simple/simpled/internal/git"
)

func initRepo(t *testing.T) (string, string) {
	t.Helper()
	root := t.TempDir()
	bareParent := t.TempDir()
	bare := filepath.Join(bareParent, "remote.git")
	run(t, root, "git", "init")
	run(t, root, "git", "config", "user.email", "t@t.com")
	run(t, root, "git", "config", "user.name", "t")
	if err := os.WriteFile(filepath.Join(root, "README.md"), []byte("hi\n"), 0o644); err != nil {
		t.Fatal(err)
	}
	run(t, root, "git", "add", "README.md")
	run(t, root, "git", "commit", "-m", "init")
	run(t, bareParent, "git", "init", "--bare", "remote.git")
	run(t, root, "git", "remote", "add", "origin", bare)
	return root, bare
}

func run(t *testing.T, dir string, name string, args ...string) {
	t.Helper()
	cmd := exec.Command(name, args...)
	cmd.Dir = dir
	out, err := cmd.CombinedOutput()
	if err != nil {
		t.Fatalf("%s %v: %s", name, args, out)
	}
}

func TestGitMethods(t *testing.T) {
	root, _ := initRepo(t)
	if err := os.WriteFile(filepath.Join(root, "change.txt"), []byte("new\n"), 0o644); err != nil {
		t.Fatal(err)
	}

	st, err := git.Status(root)
	if err != nil {
		t.Fatal(err)
	}
	if st.Branch == "" {
		t.Fatal("branch")
	}
	found := false
	for _, f := range st.Files {
		if f.Path == "change.txt" && f.Status == "?" {
			found = true
		}
	}
	if !found {
		t.Fatalf("%+v", st.Files)
	}

	diff, err := git.Diff(root, "change.txt")
	if err != nil || !strings.Contains(diff.Diff, "+++ b/change.txt") {
		t.Fatalf("diff %q err %v", diff.Diff, err)
	}
	all, err := git.Diff(root, "")
	if err != nil || all.Diff == "" {
		t.Fatalf("all diff err %v len %d", err, len(all.Diff))
	}

	hash, err := git.Commit(root, "add change", false)
	if err != nil || len(hash) < 7 {
		t.Fatalf("commit %q err %v", hash, err)
	}

	st2, err := git.Status(root)
	if err != nil {
		t.Fatal(err)
	}
	if len(st2.Files) != 0 {
		t.Fatalf("expected clean, got %+v", st2.Files)
	}
}

func TestGitStatusBranchWithoutUpstream(t *testing.T) {
	root, _ := initRepo(t)
	st, err := git.Status(root)
	if err != nil {
		t.Fatal(err)
	}
	if st.Branch == "" || st.Branch == "HEAD" {
		t.Fatalf("branch %q", st.Branch)
	}
	if st.Upstream {
		t.Fatal("expected no upstream")
	}
}

func TestGitCommitPushFailed(t *testing.T) {
	root, bare := initRepo(t)
	run(t, root, "git", "push", "-u", "origin", "HEAD")
	head, _ := exec.Command("git", "-C", root, "rev-parse", "--abbrev-ref", "HEAD").Output()
	branch := strings.TrimSpace(string(head))
	barePush := filepath.Join(bare, "refs", "heads", branch)
	if err := os.WriteFile(barePush, []byte("0000000000000000000000000000000000000000\n"), 0o644); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(root, "x.txt"), []byte("x"), 0o644); err != nil {
		t.Fatal(err)
	}
	_, err := git.Commit(root, "broken push", true)
	if err == nil || git.ErrKind(err) != "push_failed" {
		t.Fatalf("err %v", err)
	}
}
