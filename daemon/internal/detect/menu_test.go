package detect

import (
	"reflect"
	"testing"
)

const claudeTrust = `────────────────────────────────────────
 Accessing workspace:

 /home/simple/projects/simple

 Quick safety check: Is this a project you created or one you trust? (Like your own code, a well-known open source project, or work from your team). If not, take a moment to review what's in this
 folder first.

 Claude Code'll be able to read, edit, and execute files here.

 Security guide

 ❯ No, exit
   Yes, I trust this folder

 Enter to confirm · Esc to cancel`

func TestClaudeTrustMenu(t *testing.T) {
	r := ParseForKind("claude", claudeTrust)
	if r == nil {
		t.Fatal("trust prompt not detected")
	}
	if r.Title != "Is this a project you created or one you trust?" {
		t.Fatalf("title %q", r.Title)
	}
	if len(r.Options) != 2 || r.Options[1].Label != "Yes, I trust this folder" {
		t.Fatalf("options %+v", r.Options)
	}
	yes := "2"
	no := "1"
	if got := menuKeys(r.Detector, &yes); !reflect.DeepEqual(got, []string{"down", "enter"}) {
		t.Fatalf("yes keys %v", got)
	}
	if got := menuKeys(r.Detector, &no); !reflect.DeepEqual(got, []string{"enter"}) {
		t.Fatalf("no keys %v", got)
	}
}

func TestClaudeToolMenuIgnoredForHookAgents(t *testing.T) {
	screen := " Do you want to proceed?\n ❯ 1. Yes\n   2. No, and tell Claude what to do differently"
	if r := ParseForKind("claude", screen); r != nil {
		t.Fatalf("hook agent menu should be left to hooks: %+v", r)
	}
}
