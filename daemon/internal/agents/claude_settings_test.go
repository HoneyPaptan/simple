package agents

import (
	"encoding/json"
	"strings"
	"testing"
)

func TestClaudeSettingsHookCommandsSurviveBashOnWindows(t *testing.T) {
	var settings struct {
		Hooks map[string][]struct {
			Hooks []struct {
				Command string `json:"command"`
			} `json:"hooks"`
		} `json:"hooks"`
	}
	raw := claudeSettingsJSON("windows", `C:\Users\me\simpled.exe`)
	if err := json.Unmarshal([]byte(raw), &settings); err != nil {
		t.Fatal(err)
	}
	if len(settings.Hooks) == 0 {
		t.Fatal("no hooks")
	}
	for event, entries := range settings.Hooks {
		for _, entry := range entries {
			for _, h := range entry.Hooks {
				if want := `"C:/Users/me/simpled.exe" hook claude ` + event; h.Command != want {
					t.Fatalf("%s: got %q want %q", event, h.Command, want)
				}
				if strings.Contains(h.Command, `\`) {
					t.Fatalf("%s: backslash in %q", event, h.Command)
				}
			}
		}
	}
}
