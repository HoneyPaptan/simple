package install

import "testing"

func TestIsOurCommandMatchesEveryInstalledForm(t *testing.T) {
	ours := []string{
		`/home/u/.local/bin/simpled hook claude Stop`,
		`if [ -n "$HC_TERMINAL_ID" ]; then '/home/u/.local/bin/simpled' hook cursor stop; fi`,
		`if [ -n "$HC_TERMINAL_ID" ]; then "C:/Users/u/AppData/Local/simpled/bin/simpled.exe" hook cursor stop; fi`,
		`C:\Users\u\AppData\Local\simpled\bin\simpled.exe hook codex Stop`,
	}
	for _, c := range ours {
		if !isOurCommand(c) {
			t.Errorf("not recognised as ours: %s", c)
		}
	}
	for _, c := range []string{`notify-send done`, `my-simpled-wrapper hooks`, `echo simpled`} {
		if isOurCommand(c) {
			t.Errorf("wrongly claimed: %s", c)
		}
	}
}
