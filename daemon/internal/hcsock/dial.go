//go:build !windows

package hcsock

import (
	"context"
	"net"
)

func Dial(ctx context.Context, path string) (net.Conn, error) {
	var d net.Dialer
	return d.DialContext(ctx, "unix", path)
}
