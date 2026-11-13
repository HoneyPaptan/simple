//go:build windows

package hcsock

func herdrEndpoint(path string) string {
	if IsNamedPipe(path) {
		return path
	}
	return `\\.\pipe\` + path
}
