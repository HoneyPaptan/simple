package detect

func KindUsesScreenDetectors(kind string) bool {
	switch kind {
	case "claude", "codex", "opencode", "cursor":
		return false
	default:
		return true
	}
}
