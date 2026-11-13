package hookcmd

import (
	"strings"
	"testing"
)

func TestLineOnWindowsSurvivesBash(t *testing.T) {
	got := Line("windows", `C:\Users\me\AppData\Local\simpled\bin\simpled.exe`, "claude", "Stop")
	want := `"C:/Users/me/AppData/Local/simpled/bin/simpled.exe" hook claude Stop`
	if got != want {
		t.Fatalf("got %q want %q", got, want)
	}
	if strings.Contains(got, `\`) {
		t.Fatalf("backslash left in %q", got)
	}
}

func TestLineQuotesSpaces(t *testing.T) {
	if got, want := Line("windows", `C:\Users\Jo Doe\simpled.exe`, "cursor", "stop"), `"C:/Users/Jo Doe/simpled.exe" hook cursor stop`; got != want {
		t.Fatalf("got %q want %q", got, want)
	}
	if got, want := Line("linux", "/home/jo doe/it's/simpled", "codex", "Stop"), `'/home/jo doe/it'\''s/simpled' hook codex Stop`; got != want {
		t.Fatalf("got %q want %q", got, want)
	}
}
