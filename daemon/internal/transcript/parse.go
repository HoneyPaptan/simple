package transcript

import "sh.simple/simpled/internal/wire"

func parseTranscriptFile(path, kind string) ([]wire.Message, error) {
	switch kind {
	case "codex":
		return ParseCodexJSONL(path)
	case "cursor":
		return ParseCursorJSONL(path)
	default:
		return ParseClaudeJSONL(path)
	}
}
