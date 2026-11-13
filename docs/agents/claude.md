# Claude Code adapter

Facts checked on code.claude.com/docs (cli-reference, hooks) on 2026-11-04.

## Start

`claude --session-id <uuid> --permission-mode <mode> [--model <alias|name>] [--effort <level>] --settings '<inline json>' ["<first prompt>"]`

- `--session-id` must be a UUID. simpled picks it, so the transcript path is known up front: `~/.claude/projects/<cwd with every non alphanumeric character replaced by "-">/<uuid>.jsonl`.
- `--permission-mode`: `default`, `acceptEdits`, `plan`, `auto`, `dontAsk`, `bypassPermissions` (`manual` is an alias of `default`).
- `--effort`: `low`, `medium`, `high`, `xhigh`, `max` (availability depends on the model).
- `--model`: alias (`sonnet`, `opus`, `haiku`, `fable`) or a full model name.
- `--settings`: inline JSON merged over the user's settings for this session only. simpled passes its hooks this way, so it never edits the user's settings files for agents it starts.

## simple modes

| simple | Start flag | While running |
|---|---|---|
| Ask | `default` | every PermissionRequest goes to the phone |
| Plan | `plan` | plan approval goes to the phone |
| Auto | `auto` | Claude asks only for risky actions; those go to the phone |
| Full control | `bypassPermissions` | no asks |

Switching while running: moving to a more permissive simple mode is applied by simpled answering PermissionRequest hooks itself (Full control allows all; Auto allows file edits). Moving to a stricter mode takes effect from the next start; the app says so.

Model switch while running: send `/model <name>` through `terminal.send`.

## Slash commands (`agent.commands`)

Built in names and descriptions come from [code.claude.com/docs/en/commands](https://code.claude.com/docs/en/commands) (checked 2026-11-08). Custom commands are `~/.claude/commands/**/*.md` and `<cwd>/.claude/commands/**/*.md` (name from the path with `:` between folders, description from `description:` frontmatter or the first non empty body line). Skills are `~/.claude/skills/*/SKILL.md` and `<cwd>/.claude/skills/*/SKILL.md` (`name` and `description` in frontmatter).

## Hooks used

Settings shape: `{"hooks":{"<Event>":[{"matcher":"<optional>","hooks":[{"type":"command","command":"<path to simpled> hook claude <Event>","timeout":<seconds>}]}]}}`

| Event | Use |
|---|---|
| `SessionStart` | link `session_id` and `transcript_path` to `HC_TERMINAL_ID` |
| `PermissionRequest` | approval item; answer prints `{"hookSpecificOutput":{"hookEventName":"PermissionRequest","decision":{"behavior":"allow"}}}` or `"deny"` |
| `PreToolUse` matcher `AskUserQuestion` | question item; tool input carries a `questions` array (each with `question`, `header`, `options` with `label`, optional `multiSelect`) per code.claude.com/docs/en/hooks AskUserQuestion section |
| `Notification` | `permission_prompt`, `idle_prompt`, `elicitation_dialog`: status and waiting reason |
| `Stop` | done item with `last_assistant_message` |
| `UserPromptSubmit` | marks working |
| `SessionEnd` | marks done |

Common hook input: `session_id`, `transcript_path`, `cwd`, `permission_mode`, `hook_event_name`, `effort.level`. Default command hook timeout is 600 s; simpled uses 590 s for PermissionRequest and AskUserQuestion and answers before it. Hook install merge and inbox wiring are covered by daemon tests. **Needs live check:** only routine phone regression on simple-droid.

## Context window (phone meter)

Checked 2026-11-09 on [code.claude.com/docs/en/model-config](https://code.claude.com/docs/en/model-config) and [code.claude.com/docs/en/context-window](https://code.claude.com/docs/en/context-window): default context is 200,000 tokens; models with a `[1m]` variant (or names containing `[1m]`) use 1,000,000. simpled sets `Agent.context.used` from the latest assistant line in the session JSONL: `message.usage.input_tokens + cache_creation_input_tokens + cache_read_input_tokens`. Window picks 1,000,000 when the agent's `model` label or that line's `message.model` contains `[1m]`, else 200,000.
