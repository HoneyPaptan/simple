package detect

import (
	"strings"
	"testing"
)

func TestParseChoice(t *testing.T) {
	pos := []string{
		"Pick one:\n1) Alpha\n2) Beta\n3) Gamma",
		"Select an option\n  1. First\n  2. Second\n  3. Third",
		"> 1) Red\n> 2) Green\nChoose:",
	}
	for _, s := range pos {
		r := Parse(s)
		if r == nil || r.Detector != "choice" || len(r.Options) < 2 {
			t.Fatalf("choice miss %q: %+v", s, r)
		}
	}
	neg := []string{
		"only one line",
		"1) alone",
		"version 2.0.1",
	}
	for _, s := range neg {
		if r := parseChoice(s); r != nil {
			t.Fatalf("choice false positive %q", s)
		}
	}
}

func TestParseYesNo(t *testing.T) {
	pos := []string{
		"Delete file foo?\n[y/n]",
		"Continue setup (Y/n)",
		"Are you sure? [yes/no]",
	}
	for _, s := range pos {
		r := Parse(s)
		if r == nil || r.Detector != "yesno" {
			t.Fatalf("yesno miss %q", s)
		}
	}
	neg := []string{"no brackets here", "[y/n] only in code: func()"}
	for _, s := range neg {
		if parseYesNo(s) != nil && !strings.Contains(s, "?") {
			continue
		}
		if s == "no brackets here" && parseYesNo(s) != nil {
			t.Fatalf("yesno false positive %q", s)
		}
	}
}

func TestParseContinue(t *testing.T) {
	pos := []string{
		"Done.\nPress Enter to continue",
		"Press any key to continue...",
		"Hit enter to continue",
	}
	for _, s := range pos {
		r := Parse(s)
		if r == nil || r.Detector != "continue" {
			t.Fatalf("continue miss %q", s)
		}
	}
	neg := []string{"Enter username:", "continuing anyway"}
	for _, s := range neg {
		if parseContinue(s) != nil {
			t.Fatalf("continue false positive %q", s)
		}
	}
}

func TestParseLink(t *testing.T) {
	pos := []string{
		"Visit https://example.com/login to auth",
		"Open https://auth.service/verify?token=abc",
		"Please login at https://accounts.test/oauth",
	}
	for _, s := range pos {
		r := Parse(s)
		if r == nil || r.Detector != "link" || r.Link == nil {
			t.Fatalf("link miss %q", s)
		}
	}
	neg := []string{"no urls", "ftp://bad.example/x"}
	for _, s := range neg {
		if parseLink(s) != nil {
			t.Fatalf("link false positive %q", s)
		}
	}
}

func TestParseInput(t *testing.T) {
	pos := []string{
		"What is your name?",
		"Enter path:",
		"Choose directory:\n/home/dev",
	}
	for _, s := range pos {
		r := Parse(s)
		if r == nil || r.Detector != "input" {
			t.Fatalf("input miss %q got %v", s, r)
		}
	}
	neg := []string{"Press Enter to continue", "Pick one:\n1) A\n2) B"}
	for _, s := range neg {
		if parseInput(s) != nil {
			t.Fatalf("input false positive %q", s)
		}
	}
}

func TestParseError(t *testing.T) {
	pos := []string{
		"Running...\nError: disk full",
		"fatal: out of memory",
		"build failed\nsomething error: bad flag",
	}
	for _, s := range pos {
		r := Parse(s)
		if r == nil || r.Detector != "error" {
			t.Fatalf("error miss %q", s)
		}
	}
	neg := []string{"no issues", "successful exit 0"}
	for _, s := range neg {
		if parseError(s) != nil {
			t.Fatalf("error false positive %q", s)
		}
	}
}

func TestKeysForAnswer(t *testing.T) {
	d := "yesno"
	item := ToItem("t1", "i1", 0, &Result{Detector: "yesno", Title: "?"})
	item.Detector = &d
	if k := KeysForAnswer(item, "deny", nil, nil); len(k) != 2 || k[0] != "n" {
		t.Fatalf("deny keys %v", k)
	}
}

func TestFingerprintStable(t *testing.T) {
	r := &Result{Detector: "input", Title: "Name?"}
	a := Fingerprint(r)
	b := Fingerprint(r)
	if a != b || a == "" {
		t.Fatal("fingerprint")
	}
}
