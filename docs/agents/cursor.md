# Cursor CLI adapter

Facts checked on cursor.com/docs/agent/hooks on 2026-11-06 and `cursor-agent --help` (installed). Never run a real Cursor session from tests.

## Start

`cursor-agent [--model <id>] [--mode plan] [--force] [--sandbox <enabled|disabled>] --trust ["<first prompt>"]`

- `--force` (alias `--yolo`) allows commands unless explicitly denied. `--auto-review` uses a server classifier. `--plan` starts in plan mode. `--trust` skips the workspace trust prompt. `--resume [chatId]`, `--continue`.
- Models: `cursor-agent --list-models` (no model call).

## simple modes

| simple | Start flags |
|---|---|
| Ask | none |
| Plan | `--plan` |
| Auto | `--auto-review` |
| Full control | `--force` |

## Hooks

`~/.cursor/hooks.json` (user) or `<project>/.cursor/hooks.json`: `{"version":1,"hooks":{"<event>":[{"command":"...","timeout":<s>}]}}`. Event names are camelCase.

Common stdin: `conversation_id`, `generation_id`, `model`, `hook_event_name`, `cursor_version`, `workspace_roots`, `user_email`, `transcript_path`.

| Event | Extra stdin | Output | Use |
|---|---|---|---|
| `sessionStart` | `session_id`, `composer_mode` | `env`, `additional_context` | link session and `transcript_path` to `HC_TERMINAL_ID` |
| `beforeShellExecution` | `command`, `cwd`, `sandbox` | `{"permission":"allow"|"deny"|"ask"}` | approval item; `ask` falls back to Cursor's own prompt |
| `beforeMCPExecution` | `tool_name`, `tool_input`, `mcp_server_name` | same | approval item |
| `beforeSubmitPrompt` | `prompt` | `{"continue":true}` | marks working |
| `afterAgentResponse` | `text` | none | last message |
| `stop` | `status` (`completed`, `aborted`, `error`), `loop_count` | none needed | done item |
| `sessionEnd` | `reason`, `final_status` | none | marks done |

Exit 2 means deny; other non zero codes fail open. Invalid JSON from a permission hook blocks the action, so simpled must always print valid JSON (`{"permission":"ask"}` when it has no answer).

The docs do not say whether agent hooks run in the CLI. If a live check on simple-droid shows they do not, approvals fall back to screen detection like shell agents.

## Transcript

`transcript_path` from hooks; files live in `~/.cursor/projects/<cwd slug>/agent-transcripts/<id>/<id>.jsonl`. Lines seen: `{"role":"user"|"assistant","message":{"content":[{"type":"text","text":"..."}]}}` and `{"type":"...","status":"..."}`. Verified 2026-11-06 from three small local transcripts (secrets stripped) into `daemon/internal/transcript/testdata/cursor_transcript.jsonl`. `cursor-agent --list-models` prints `id - label` lines; simpled caches the parsed list for one hour. **Needs live check:** permission hooks firing in `cursor-agent` on simple-droid (docs ambiguous); if not, screen detection applies.

## Workspace trust

simpled always passes `--trust`. Cursor's trust prompt is a terminal dialog that a GUI only client cannot answer, and the user has already chosen the folder by starting the agent there from the app. Tradeoff: project level Cursor config in that folder (hooks, MCP servers) loads without a second confirmation, so the app only offers folders the user added as workspaces.
