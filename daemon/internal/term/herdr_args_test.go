package term

import (
	"slices"
	"testing"
)

func TestHerdrAttachTakesOverStaleClients(t *testing.T) {
	if !slices.Contains(herdrAgentAttachArgs("w1:p1"), "--takeover") {
		t.Fatal("agent attach without --takeover")
	}
	if !slices.Contains(herdrTerminalAttachArgs("term_1"), "--takeover") {
		t.Fatal("terminal attach without --takeover")
	}
}
