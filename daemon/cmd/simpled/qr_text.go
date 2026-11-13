package main

import "strings"

func qrLines(size int, black func(x, y int) bool) []string {
	dark := func(x, y int) bool { return y < size && black(x, y) }
	var lines []string
	for y := 0; y < size; y += 2 {
		var b strings.Builder
		b.WriteString("  ")
		for x := 0; x < size; x++ {
			switch top, bottom := dark(x, y), dark(x, y+1); {
			case top && bottom:
				b.WriteString("█")
			case top:
				b.WriteString("▀")
			case bottom:
				b.WriteString("▄")
			default:
				b.WriteString(" ")
			}
		}
		lines = append(lines, b.String())
	}
	return lines
}
