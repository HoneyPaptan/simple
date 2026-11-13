package hooks

import (
	"encoding/json"
	"testing"
)

func TestApprovalTitleFromDescription(t *testing.T) {
	raw, _ := json.Marshal(map[string]string{
		"command":     "npm run build",
		"description": "Clean build",
	})
	title := approvalTitle("Bash", raw)
	if title != "Clean build" {
		t.Fatalf("title %q", title)
	}
	if sub := approvalToolSubtitle("Bash", title); sub != "Bash" {
		t.Fatalf("sub %q", sub)
	}
	if sub := approvalToolSubtitle("Bash", "Bash"); sub != "" {
		t.Fatalf("sub %q", sub)
	}
}

func TestCursorApprovalShellDescription(t *testing.T) {
	raw, _ := json.Marshal(map[string]string{"description": "Clean build", "command": "npm test"})
	title, tool, cmd := cursorApprovalFields("beforeShellExecution", cursorInput{
		Command:   "npm test",
		ToolInput: raw,
	})
	if title != "Clean build" || tool != "Shell" || cmd != "npm test" {
		t.Fatalf("title=%q tool=%q cmd=%q", title, tool, cmd)
	}
}

func TestCursorApprovalMCPDescription(t *testing.T) {
	raw, _ := json.Marshal(map[string]string{"description": "List files"})
	title, tool, _ := cursorApprovalFields("beforeMCPExecution", cursorInput{
		ToolName:      "list_dir",
		McpServerName: "workspace",
		ToolInput:     raw,
	})
	if title != "List files" || tool != "list_dir" {
		t.Fatalf("title=%q tool=%q", title, tool)
	}
}
