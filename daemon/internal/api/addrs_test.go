package api

import (
	"net"
	"strings"
	"testing"
)

func TestTailnetAddrsUseCIDRNotIfaceNames(t *testing.T) {
	_, err := tailnetAddrs()
	if err != nil {
		t.Fatal(err)
	}
}

func TestListenAddrsNeverWildcard(t *testing.T) {
	for _, lan := range []bool{false, true} {
		addrs, err := listenAddrs(47420, lan)
		if err != nil {
			t.Fatal(err)
		}
		for _, a := range addrs {
			host, _, err := net.SplitHostPort(a)
			if err != nil {
				t.Fatal(a, err)
			}
			if host == "0.0.0.0" || host == "::" {
				t.Fatalf("wildcard bind %q lan=%v", a, lan)
			}
			if strings.Contains(host, "0.0.0.0") {
				t.Fatalf("unexpected host %q", host)
			}
		}
	}
}

func TestPairAddrsSkipLoopbackAndPreferIPv4(t *testing.T) {
	for _, lan := range []bool{false, true} {
		pair, err := PairAddrs(47420, lan)
		if err != nil {
			t.Fatal(err)
		}
		v4 := false
		for _, a := range pair {
			if strings.HasPrefix(a, "127.") {
				t.Fatalf("loopback in pair addrs %q", a)
			}
			if !strings.HasPrefix(a, "[") {
				v4 = true
			}
		}
		if v4 {
			for _, a := range pair {
				if strings.HasPrefix(a, "[") {
					t.Fatalf("ipv6 %q next to an ipv4 address in %v", a, pair)
				}
			}
		}
	}
}
