package main

import (
	"testing"

	"rsc.io/qr"
)

func TestQRLinesKeepEveryModuleInHalfTheRows(t *testing.T) {
	code, err := qr.Encode("simple://pair?v=1&n=box&a=100.64.0.1:47420&f=ab&c=123456", qr.M)
	if err != nil {
		t.Fatal(err)
	}
	lines := qrLines(code.Size, code.Black)
	if want := (code.Size + 1) / 2; len(lines) != want {
		t.Fatalf("got %d lines, want %d", len(lines), want)
	}
	for row, line := range lines {
		cells := []rune(line)[2:]
		if len(cells) != code.Size {
			t.Fatalf("line %d has %d cells, want %d", row, len(cells), code.Size)
		}
		for x, c := range cells {
			top := code.Black(x, 2*row)
			bottom := 2*row+1 < code.Size && code.Black(x, 2*row+1)
			want := map[[2]bool]rune{{true, true}: '█', {true, false}: '▀', {false, true}: '▄', {false, false}: ' '}[[2]bool{top, bottom}]
			if c != want {
				t.Fatalf("cell %d,%d is %q, want %q", x, row, c, want)
			}
		}
	}
}
