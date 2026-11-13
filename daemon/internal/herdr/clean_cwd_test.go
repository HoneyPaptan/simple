package herdr

import "testing"

func TestPaneCwdsDropDeletedSuffix(t *testing.T) {
	p := PaneInfo{Cwd: "/w/app-w2 (deleted)", ForegroundCwd: " /w/app-w2 (deleted) "}
	p.cleanCwds()
	if p.Cwd != "/w/app-w2" || p.ForegroundCwd != "/w/app-w2" {
		t.Fatalf("got %q %q", p.Cwd, p.ForegroundCwd)
	}
}
