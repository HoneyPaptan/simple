//go:build windows

package hcsock

import (
	"context"
	"net"

	"github.com/Microsoft/go-winio"
)

func Dial(ctx context.Context, path string) (net.Conn, error) {
	if IsNamedPipe(path) {
		return winio.DialPipeContext(ctx, path)
	}
	var d net.Dialer
	return d.DialContext(ctx, "unix", path)
}
