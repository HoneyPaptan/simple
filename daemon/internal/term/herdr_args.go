package term

func herdrAgentAttachArgs(target string) []string {
	return []string{"agent", "attach", target, "--takeover"}
}

func herdrTerminalAttachArgs(terminalID string) []string {
	return []string{"terminal", "attach", "--takeover", terminalID}
}
