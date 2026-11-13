# Codex adapter

Facts checked on learn.chatgpt.com/docs (hooks, cli/reference) on 2026-11-06. Codex is not installed on the dev machine; never run a real Codex session.

## Slash commands (`agent.commands`)

Built in names and descriptions come from [openai/codex `codex-rs/tui/src/slash_command.rs`](https://github.com/openai/codex/blob/main/codex-rs/tui/src/slash_command.rs) (checked 2026-11-08). Custom prompts are `~/.codex/prompts/*.md` as `prompts:<basename>`.

## Start

`codex [-m <model>] --ask-for-approval <on-request|never> --sandbox <read-only|workspace-write|danger-full-access> ["<first prompt>"]`

- `--dangerously-bypass-approvals-and-sandbox` (alias `--yolo`) runs every command without asks or sandbox.
- `--approve-for-me` means automatic approval review with `on-request` and `workspace-write`.
- `-c key=value` overrides config (TOML values).
- Resume: `codex resume [SESSION_ID]`.

## simple modes

| simple | Start flags |
|---|---|
| Ask | `--ask-for-approval on-request --sandbox workspace-write` |
| Plan | not offered |
| Auto | `--approve-for-me` |
| Full control | `--dangerously-bypass-approvals-and-sandbox` |

## Hooks

Hooks live in `~/.codex/hooks.json` (user) or `<repo>/.codex/hooks.json` (trusted projects only). `[features] hooks = false` in `config.toml` turns them off. Same shape as Claude: `{"hooks":{"<Event>":[{"matcher":"","hooks":[{"type":"command","command":"...","timeout":600}]}]}}`.

Every hook gets on stdin `session_id`, `transcript_path`, `cwd`, `hook_event_name`, `model`; most also `permission_mode`.

| Event | Extra stdin | Use |
|---|---|---|
| `SessionStart` | `source` | link `session_id`, `transcript_path` to `HC_TERMINAL_ID` |
| `PermissionRequest` | `turn_id`, `tool_name`, `tool_input` (`command`, `description`) | approval item; answer `{"hookSpecificOutput":{"hookEventName":"PermissionRequest","decision":{"behavior":"allow"}}}` or `"behavior":"deny","message":"..."`; print nothing to fall back to Codex's own prompt |
| `UserPromptSubmit` | `turn_id`, `prompt` | marks working |
| `Stop` | `turn_id`, `last_assistant_message` | done item |
| `SessionEnd` | `reason` | marks done (default timeout 1 s, max 3 s) |

There is no Notification event. Default hook timeout is 600 s; simpled uses 590 s for PermissionRequest.

## Transcript

Path comes from the `SessionStart` hook (`transcript_path`), a rollout JSONL. Verified 2026-11-06 against `openai/codex` `codex-rs/history/src/rollout_payload.rs` and `codex-rs/rollout/src/lib.rs`: each line is a JSON object with top level `timestamp`, optional `ordinal`, and a tagged `type` (`session_meta`, `response_item`, `event_msg`, `turn_context`, …) whose body lives in `payload` (response items use nested `type` such as `message` with `input_text` / `output_text` content). Daemon hook and parser tests use hand built fixtures. **Needs live check:** rollout files and PermissionRequest on simple-droid with a fake `codex` on PATH.

## Chat

Rollout `response_item` types map to protocol messages as follows.

| Codex payload `type` | Chat role | Tool `name` / notes |
|---|---|---|
| `message` / `agent_message` (text) | user or assistant | — |
| `reasoning` | skipped | — |
| `function_call` | tool | `name` from payload (`exec_command`, `shell`, `update_plan`, …); `arguments` JSON string |
| `function_call_output` | updates prior tool | `call_id`; `output` plain string or content items |
| `custom_tool_call` / `custom_tool_call_output` | tool | e.g. `apply_patch` with `input` patch text |
| `local_shell_call` | tool | `local_shell`; summary from `action.command` |
| `web_search_call` | tool | `web_search`; summary from `action.query` |

`event_msg` `user_message` / `agent_message` text is deduped with matching `response_item` messages by hashed id. Injected user text (`<environment_context>`, `<user_instructions>`, `<permissions instructions>`, `# AGENTS.md instructions`, `<turn_aborted>`) is dropped. `turn_aborted` becomes assistant text `Codex stopped` plus optional `: <reason>`; still-running tools become `error`.

## Context window (phone meter)

Checked 2026-11-09 against `openai/codex` `codex-rs/protocol/src/protocol.rs` (`TokenUsage::tokens_in_context_window`, `percent_of_context_window_remaining`, `BASELINE_TOKENS = 12000`) and `codex-rs/tui/src/chatwidget.rs` (`context_remaining_percent`). simpled reads the latest rollout `event_msg` with `payload.type` `token_count`, takes `info.model_context_window` and `info.last_token_usage` (uses `total_tokens` when set, else sums input, cache, output, and reasoning fields), then publishes `Agent.context` with `used = max(total_tokens - 12000, 0)` and `window = model_context_window - 12000` so the app’s `100 - used/window` matches Codex’s baseline adjusted remainder.
