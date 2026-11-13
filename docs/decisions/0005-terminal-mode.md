# 0005: Terminal mode

Chat stays the main view and is built only from structured data (transcripts and hooks). For moments that only a TUI can handle, the chat can switch to a real terminal on the same agent.

- Host: `simpled` runs `hc attach --history 1000 <terminal>` inside a PTY it opens itself (plain `/dev/ptmx` ioctls, no new Go dependency) and streams the bytes over the existing WebSocket as terminal frames. Using the `hc` CLI keeps us off herdr-core's undocumented framed wire format.
- App: `com.github.termux.termux-app:terminal-emulator` and `terminal-view` 0.118.1 from JitPack (Apache 2.0, see the Termux LICENSE.md). We use only `TerminalEmulator` (parses the bytes) and `TerminalRenderer` (draws to a Canvas) inside our own view; Termux's `TerminalSession` and `TerminalView` are not used because they assume a local process.

Supersedes the "never a terminal emulator" rule.
