package detect

import "testing"

func TestActiveYesNoTitle(t *testing.T) {
	screen := "simple@instabox:~/demo$ read -p \"Overwrite config.json? [y/N] \" a\nOverwrite config.json? [y/N] "
	r := Parse(screen)
	if r == nil || r.Detector != "yesno" {
		t.Fatalf("parse: %+v", r)
	}
	if r.Title != "Overwrite config.json?" {
		t.Fatalf("title %q", r.Title)
	}
	if r.Body != nil {
		t.Fatalf("body should be empty, got %q", *r.Body)
	}
}

func TestAnsweredPromptInactive(t *testing.T) {
	screen := "simple@instabox:~/demo$ read -p \"Overwrite config.json? [y/N] \" a\nOverwrite config.json? [y/N] \nyou said y\nsimple@instabox:~/demo$ "
	if r := Parse(screen); r != nil {
		t.Fatalf("expected inactive, got %+v", r)
	}
}

func TestReadPromptExact(t *testing.T) {
	waiting := "simple@host:~/demo$ read -p \"Overwrite config.json? [y/N] \" a\nOverwrite config.json? [y/N] "
	r := Parse(waiting)
	if r == nil || r.Title != "Overwrite config.json?" {
		t.Fatalf("got %+v", r)
	}
}
