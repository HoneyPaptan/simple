package main

import (
	"encoding/base64"
	"encoding/hex"
	"fmt"
	"io"
	"strings"
)

func pairLink(host string, addrs []string, fingerprintHex, code string) (string, error) {
	fp, err := hex.DecodeString(fingerprintHex)
	if err != nil {
		return "", err
	}
	return "simple://pair?v=1&n=" + host + "&a=" + strings.Join(addrs, ",") +
		"&f=" + base64.RawURLEncoding.EncodeToString(fp) + "&c=" + code, nil
}

func writePairing(w io.Writer, qrRows []string, link string) {
	fmt.Fprintln(w)
	for _, row := range qrRows {
		fmt.Fprintln(w, row)
	}
	fmt.Fprintln(w)
	fmt.Fprintln(w, "Scan this with the simple app. The code works once and expires in 5 minutes.")
	fmt.Fprintln(w, "Or copy this link into Enter code on the phone:")
	fmt.Fprintln(w)
	fmt.Fprintln(w, link)
	fmt.Fprintln(w)
}
