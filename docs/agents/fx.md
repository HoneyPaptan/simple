# fx adapter

Facts from `fx --help`, `fx status --json` and `fx permissions` on the dev machine (fx v0.0.15), 2026-11-06. Never start a real fx session.

## Start

`fx [--model <id>] [--effort <level>] [--provider <name>]`. No first prompt flag for interactive mode: send the prompt with `terminal.send` after start. Resume: `fx --resume <id>`.

## Modes

`fx status --json` lists modes `auto`, `ask`, `full-access`; there is no start flag for them. simple shows the mode read from `fx status --json` and does not offer a switch.

## Hooks

None. Status and approvals come from screen detection (the `detect` package), same as shell agents, and the chat screen uses the screen peek.

## Transcript

Sessions live in `~/.fx/sessions/<id>/` (`session.json` with `id`, `workspace_root`, `model`; `events.jsonl`). Every local `events.jsonl` is empty, so the format is unknown: no transcript parser in v1.

## Verified in repo

`ParseFxStatusJSON` and `FxModelsResult` are covered by daemon unit tests using `daemon/internal/agents/testdata/fx_status.json`. Prompt after start uses the same `SendPromptWhenQuiet` path as shell. **Needs live check:** approvals and chat on simple-droid with a fake `fx` on PATH.
