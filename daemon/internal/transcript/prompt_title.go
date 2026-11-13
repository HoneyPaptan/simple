package transcript

import (
	"os"
)

func FirstUserPrompt(path, kind string) (string, error) {
	if kind != "claude" {
		return "", nil
	}
	msgs, err := ParseClaudeJSONL(path)
	if err != nil {
		if os.IsNotExist(err) {
			return "", nil
		}
		return "", err
	}
	for _, m := range msgs {
		if m.Role != "user" || m.Text == nil {
			continue
		}
		return *m.Text, nil
	}
	return "", nil
}
