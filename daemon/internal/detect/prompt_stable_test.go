package detect

import "testing"

func TestCommandEchoLineIsNotPrompt(t *testing.T) {
	echo := "simple@instabox:~/demo$ clear; select f in apple banana cherry"
	if !isCommandEcho(echo) {
		t.Fatal("expected command echo")
	}
	screen := echo + "\n1) apple\n2) banana\n3) cherry"
	if r := Parse(screen); r != nil && isCommandEcho(r.Title) {
		t.Fatalf("echo title must not become prompt: %+v", r)
	}
}

func TestSelectPromptNeedsStablePolls(t *testing.T) {
	screen := "Pick fruit\n#?\n1) apple\n2) banana\n3) cherry"
	r := Parse(screen)
	if r == nil || r.Detector != "choice" {
		t.Fatalf("parse: %+v", r)
	}
	if r.Title != "Pick fruit" {
		t.Fatalf("title %q", r.Title)
	}
}

func TestChoiceTitleSkipsHashPrompt(t *testing.T) {
	screen := "simple@box:~/demo$ select f in a b c\n#?\n1) a\n2) b\n3) c"
	r := Parse(screen)
	if r == nil || r.Detector != "choice" {
		t.Fatalf("parse: %+v", r)
	}
	if r.Title == "#?" {
		t.Fatalf("title must not be hash prompt")
	}
}
