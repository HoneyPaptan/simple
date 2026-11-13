package main

import (
	"strings"
	"testing"
)

func TestPairLinkUsesShortFingerprint(t *testing.T) {
	fp := strings.Repeat("ab", 32)
	link, err := pairLink("box", []string{"100.64.0.1:47420"}, fp, "abcdefghijklmnopqrstuv")
	if err != nil {
		t.Fatal(err)
	}
	want := "simple://pair?v=1&n=box&a=100.64.0.1:47420&f=q6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6s&c=abcdefghijklmnopqrstuv"
	if link != want {
		t.Fatalf("got %s", link)
	}
}

func TestPairingTextKeepsLinkOnOneLine(t *testing.T) {
	link := "simple://pair?v=1&n=box&a=100.64.0.1:47420&f=" + strings.Repeat("q", 43) + "&c=abcdefghijklmnopqrstuv"
	var out strings.Builder
	writePairing(&out, []string{"##", "##"}, link)
	found := false
	for _, line := range strings.Split(out.String(), "\n") {
		if line == link {
			found = true
		}
	}
	if !found {
		t.Fatalf("link is not alone on one line:\n%s", out.String())
	}
}
