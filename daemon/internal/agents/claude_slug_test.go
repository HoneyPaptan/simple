package agents

import "testing"

func TestProjectSlugMatchesClaudeFolderNames(t *testing.T) {
	cases := map[string]string{
		`C:\Users\me\proj`:         "C--Users-me-proj",
		`C:\Users\me\.data\proj`:   "C--Users-me--data-proj",
		"/home/me/projects/app":    "-home-me-projects-app",
		"/home/me/.t3/scratch/a_b": "-home-me--t3-scratch-a-b",
	}
	for cwd, want := range cases {
		if got := projectSlug(cwd); got != want {
			t.Fatalf("%s: got %q want %q", cwd, got, want)
		}
	}
}

func TestCursorProjectSlugWindowsPath(t *testing.T) {
	got := CursorProjectSlug(`C:\Users\me\proj`)
	want := "C:-Users-me-proj"
	if got != want {
		t.Fatalf("got %q want %q", got, want)
	}
}
