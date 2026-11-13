package install

import (
	"runtime"

	"sh.simple/simpled/internal/hookcmd"
)

func hookShell(simpledPath, kind, event string) string {
	return `if [ -n "$HC_TERMINAL_ID" ]; then ` + hookcmd.Line(runtime.GOOS, simpledPath, kind, event) + `; fi`
}
