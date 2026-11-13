//go:build !windows

package hcsock

func herdrEndpoint(path string) string {
	return path
}
