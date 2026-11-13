package files_test

import (
	"os"
	"path/filepath"
	"strings"
	"testing"

	"sh.simple/simpled/internal/files"
)

func TestResolvePath(t *testing.T) {
	home, err := os.UserHomeDir()
	if err != nil {
		t.Fatal(err)
	}
	cases := []struct {
		in    string
		ok    bool
		want  string
		kind  string
	}{
		{home + "/demo", true, filepath.Clean(home + "/demo"), ""},
		{"~/demo", true, filepath.Clean(home + "/demo"), ""},
		{"", false, "", "bad_request"},
		{"relative", false, "", "bad_request"},
		{"../outside", false, "", "bad_request"},
	}
	for _, c := range cases {
		got, err := files.ResolvePath(c.in)
		if c.ok {
			if err != nil || got != c.want {
				t.Errorf("%q: got %q err %v want %q", c.in, got, err, c.want)
			}
		} else {
			if err == nil {
				t.Errorf("%q: expected error", c.in)
			} else if c.kind != "" && files.ErrKind(err) != c.kind {
				t.Errorf("%q: kind %s want %s", c.in, files.ErrKind(err), c.kind)
			}
		}
	}
}

func TestFilesMethods(t *testing.T) {
	root := t.TempDir()
	sub := filepath.Join(root, "nested")
	if err := os.Mkdir(sub, 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(root, "a.txt"), []byte("line1\nline2\nline3"), 0o644); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(root, "bin.dat"), []byte{0, 1, 2}, 0o644); err != nil {
		t.Fatal(err)
	}
	big := filepath.Join(root, "big.txt")
	if err := os.WriteFile(big, []byte(strings.Repeat("x", 8*1024*1024+1)), 0o644); err != nil {
		t.Fatal(err)
	}

	type errCase struct {
		method string
		fn     func() error
		kind   string
	}
	errs := []errCase{
		{"files.list", func() error {
			_, err := files.List(filepath.Join(root, "missing"))
			return err
		}, "not_found"},
		{"files.list", func() error {
			_, err := files.List(filepath.Join(root, "a.txt"))
			return err
		}, "bad_request"},
		{"files.read", func() error {
			_, err := files.Read(filepath.Join(root, "missing"), nil, nil)
			return err
		}, "not_found"},
		{"files.read", func() error {
			_, err := files.Read(filepath.Join(root, "bin.dat"), nil, nil)
			return err
		}, "binary"},
		{"files.read", func() error {
			_, err := files.Read(big, nil, nil)
			return err
		}, "too_large"},
	}
	for _, c := range errs {
		t.Run(c.method+"_"+c.kind, func(t *testing.T) {
			err := c.fn()
			if err == nil {
				t.Fatal("expected error")
			}
			if files.ErrKind(err) != c.kind {
				t.Fatalf("kind %s want %s", files.ErrKind(err), c.kind)
			}
		})
	}

	list, err := files.List(root)
	if err != nil {
		t.Fatal(err)
	}
	if len(list.Entries) < 3 {
		t.Fatalf("entries %d", len(list.Entries))
	}
	if !list.Entries[0].Dir {
		t.Fatal("dirs first")
	}

	readAll, err := files.Read(filepath.Join(root, "a.txt"), nil, nil)
	if err != nil {
		t.Fatal(err)
	}
	if readAll.Lines != 3 || readAll.Truncated {
		t.Fatalf("%+v", readAll)
	}
	off := 1
	lim := 1
	readPage, err := files.Read(filepath.Join(root, "a.txt"), &off, &lim)
	if err != nil || readPage.Lines != 1 || !readPage.Truncated || strings.TrimSpace(readPage.Text) != "line2" {
		t.Fatalf("%+v err %v", readPage, err)
	}

	target := filepath.Join(root, "new.txt")
	if err := files.Write(target, "hello"); err != nil {
		t.Fatal(err)
	}
	st, _ := os.Stat(target)
	if st.Mode().Perm() != 0o644 {
		t.Fatalf("mode %o", st.Mode())
	}
	if err := files.Write(target, "updated"); err != nil {
		t.Fatal(err)
	}
	body, _ := os.ReadFile(target)
	if string(body) != "updated" {
		t.Fatalf("body %q", body)
	}
}

func TestReadWholeFileKeepsLineEnding(t *testing.T) {
	root := t.TempDir()
	for name, body := range map[string]string{
		"nl.txt":    "a\nb\n",
		"nonl.txt":  "a\nb",
		"blank.txt": "a\n\n",
		"crlf.txt":  "a\r\nb\r\n",
	} {
		p := filepath.Join(root, name)
		if err := os.WriteFile(p, []byte(body), 0o644); err != nil {
			t.Fatal(err)
		}
		got, err := files.Read(p, nil, nil)
		if err != nil {
			t.Fatal(err)
		}
		if got.Text != body {
			t.Fatalf("%s: got %q want %q", name, got.Text, body)
		}
	}
}

func TestMkdir(t *testing.T) {
	home := t.TempDir()
	t.Setenv("HOME", home)
	parent := filepath.Join(home, "parent")
	if err := os.Mkdir(parent, 0o755); err != nil {
		t.Fatal(err)
	}
	if err := files.Mkdir(parent, "child"); err != nil {
		t.Fatal(err)
	}
	st, err := os.Stat(filepath.Join(parent, "child"))
	if err != nil || !st.IsDir() {
		t.Fatalf("stat %+v err %v", st, err)
	}
	if st.Mode().Perm()&0o777 != 0o755 {
		t.Fatalf("mode %o", st.Mode().Perm())
	}
	if err := files.Mkdir(parent, "child"); err == nil || files.ErrKind(err) != "exists" {
		t.Fatalf("exists err %v", err)
	}
	cases := []struct {
		name string
		kind string
	}{
		{"", "invalid_name"},
		{".", "invalid_name"},
		{"..", "invalid_name"},
		{"a/b", "invalid_name"},
	}
	for _, c := range cases {
		err := files.Mkdir(parent, c.name)
		if err == nil || files.ErrKind(err) != c.kind {
			t.Fatalf("%q: err %v", c.name, err)
		}
	}
	outside := filepath.Join(home, "..", "outside-mkdir")
	if err := files.Mkdir(outside, "x"); err == nil || files.ErrKind(err) != "outside_home" {
		t.Fatalf("outside err %v", err)
	}
}

func TestListShowsSymlinkedFolderAsFolder(t *testing.T) {
	dir := t.TempDir()
	target := filepath.Join(dir, "real")
	if err := os.Mkdir(target, 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.Symlink(target, filepath.Join(dir, "link")); err != nil {
		t.Fatal(err)
	}
	res, err := files.List(dir)
	if err != nil {
		t.Fatal(err)
	}
	for _, e := range res.Entries {
		if e.Name == "link" && !e.Dir {
			t.Fatalf("link listed as file: %+v", e)
		}
	}
}
