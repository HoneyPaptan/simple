# Herdr TUI socket API (simpled reference)

Herdr is separate from herdr-core (`hc`). simpled talks to the Herdr server over a Unix domain socket using newline-delimited JSON requests.

## Socket path

1. `HERDR_SOCKET_PATH` when set
2. Otherwise `$HOME/.config/herdr/herdr.sock`

Override via simpled config store field `HerdrSock` (tests only).

## Request and response

Each request is one JSON object per line:

```json
{"id":"req-1","method":"agent.list","params":{}}
```

Success:

```json
{"id":"req-1","result":{"type":"agent_list","agents":[...]}}
```

Error:

```json
{"id":"","error":{"code":"...","message":"..."}}
```

`id` is a string. Params are always an object (use `{}` when empty).

Protocol version comes from `ping` (`result.protocol`, `result.version`).

## Agents

`agent.list` returns `agents[]` with at least:

| Field | Role |
|-------|------|
| `pane_id` | Stable pane handle (`w1:p3`) |
| `agent` | Detected kind (`claude`, `codex`, `cursor`, …) |
| `agent_status` | `idle`, `working`, `blocked`, `done`, `unknown` |
| `agent_session` | Session binding (`kind` `id` or `path`, `value`, `source`, `agent`) |
| `name` | Optional live agent name |
| `cwd`, `foreground_cwd` | Working directory |
| `terminal_title_stripped` | Fallback title |
| `revision`, `state_change_seq` | Change detection |
| `workspace_id`, `tab_id`, `terminal_id` | Layout ids |

simpled agent ids: `h:` + `pane_id` so they never collide with hc terminal addresses.

## Screen read

`agent.read` params: `target` (pane id or unique agent name), `source` (`recent_unwrapped` for transcripts), `format` (`text` or `ansi`), optional `lines`, `strip_ansi`.

## Send prompt and keys

| Method | Params |
|--------|--------|
| `agent.prompt` | `target`, `text`; optional `wait` |
| `agent.send_keys` | `target`, `keys` (e.g. `ctrl+c`, `esc`, `enter`) |

Interrupt on the phone maps to `agent.send_keys` with `ctrl+c` for herdr agents.

## Start in a new pane

Typical flow for starting from simpled:

1. `tab.create` with `cwd`, `label`, optional `workspace_id`, `focus: false`
2. Read `result.root_pane.pane_id` from the response
3. `agent.start` with `name`, `kind`, `pane_id`, optional `args` after agent-specific flags

## Layout helpers

| Method | Use |
|--------|-----|
| `session.snapshot` | Full session state |
| `tab.create` | New tab in a workspace |
| `pane.split` | Split for an extra shell pane |
| `workspace.list` | Workspaces |

## Change feed

`events.subscribe` accepts `subscriptions: [{type: "layout.updated"}, {type: "pane.agent_detected"}, ...]`. Some subscription kinds require a `pane_id`.

simpled subscribes to coarse events when the socket stays up, and polls `agent.list` at most every 1 s, diffing `revision` and `state_change_seq`.

## Terminal attach (phone terminal mode)

There is no attach RPC on this schema. Non-takeover attach uses the CLI:

```text
herdr agent attach <target>
```

`--takeover` steals the pane from the desktop client; simpled must not use it for normal viewing.

## CLI discovery

`herdr --skill`, `herdr api schema`, and `herdr api schema --json` are authoritative for the installed binary.
