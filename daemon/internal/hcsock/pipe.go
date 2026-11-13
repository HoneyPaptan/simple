package hcsock

import (
	"fmt"
	"strings"
)

func WindowsDefaultPipe(user, session string) string {
	return fmt.Sprintf(`\\.\pipe\herdr-core-%s-%s.sock`, sanitizePipeUser(user), session)
}

func sanitizePipeUser(user string) string {
	if user == "" {
		return "user"
	}
	var b strings.Builder
	for _, r := range user {
		if (r >= 'a' && r <= 'z') || (r >= 'A' && r <= 'Z') || (r >= '0' && r <= '9') {
			b.WriteRune(r)
		}
	}
	if b.Len() == 0 {
		return "user"
	}
	return b.String()
}
