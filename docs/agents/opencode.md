# OpenCode adapter


## Chat (OpenCode 2, checked 2026-11-08)

OpenCode 2 runs a shared background server; plugins load inside that server, not the TUI, so `HC_TERMINAL_ID` is never set there and the plugin below stays silent for phone started agents. Chat therefore comes from the server's own API:

- simpled starts the TUI as `opencode --session ses_<26 hex> [--auto] <cwd>` (the flag creates the session when it does not exist) and labels the terminal `simple.session`.
- It reads `~/.local/state/opencode/service.json` (`url`, `password`) and calls `GET /api/session/{id}/message?order=asc` with basic auth user `opencode`, every 500 ms while the chat is open.
- Item `type` `user` and `assistant` become messages; assistant `content` blocks `text` and `tool` (`name`, `state.status`, `state.input`, `state.content`, `state.error` string or `{type,message}`, `state.metadata.files[].patch`) map onto `Message.tool`; a `type: idle` with `outcome: failed` becomes a visible "OpenCode stopped" line. Reasoning, system, synthetic and compaction items are skipped.
- Slash commands: `agent.commands` lists `GET /api/command` on the local OpenCode server (`data[].name`, `data[].description`), same basic auth as other session API calls.
- Models: `agent.models` lists `GET /api/model` (enabled, not deprecated) as `<providerID>/<id>` with variants as efforts, after Default; `agent.configure` calls `POST /api/session/{id}/model` with `{model:{id, providerID, variant?}}`. `--model` is not a TUI flag in 2.x.
- Finished: every second simpled reads `GET /api/session/active` (sessions absent from `data` are idle); when an agent's session leaves that set it marks the agent done and adds a Finished item with the last reply, which drives the phone notification.
- Stop: `agent.interrupt` calls `POST /api/session/{id}/interrupt`, falling back to the terminal key when the API fails.
- Permissions: OpenCode allows everything unless a rule says `ask`, so simpled sets per session rules from the agent mode with `PATCH /api/session/{id}` `{permissions:[{action, resource, effect}]}` (later rules win): Ask starts from `* ask` and allows only read only actions (`read`, `list`, `glob`, `grep`, `todoread`, `todowrite`, `lsp`, `skill`, `question`); Auto also allows `edit`, `write`, `patch`, `multiedit`; Full allows all. It reapplies when the mode changes and every minute, so a restarted OpenCode server gets them back. The TUI starts without `--auto`, so switching Full back to Ask takes effect. Every second it lists `GET /api/session/{id}/permission` for each OpenCode agent, turns new requests into approval items, replies with `POST .../permission/{requestID}/reply` `{decision: once|always|reject}` when answered on the phone, and resolves the item when the request disappears (answered in the TUI).

Facts checked on opencode.ai/docs (plugins, sdk) on 2026-11-06. Installed `opencode` is v2.2.5; plugin type packages on this machine are `@opencode-ai/plugin` and `@opencode-ai/sdk` v1.19.5 under `~/.opencode/node_modules/` (OpenCode 2 may ship a different client at runtime). Never run a real OpenCode session (no model calls, nothing with `OPENCODE_BIN`).

## Start

`opencode [--model <provider/model>] [<project dir>]`. Check `opencode --help` locally for the prompt and agent flags before using them.

## Hooks: a plugin

OpenCode has no hooks.json. simpled installs a plugin file `~/.config/opencode/plugins/simple.ts` (global plugins load at startup):

```ts
export const Simple = async ({ client, directory }) => ({ ... })
```

- Events forwarded (fire and forget): `session.created`, `session.status`, `session.idle`, `session.error`, `message.updated`, `message.part.updated`, `permission.replied`.
- The plugin runs only when `HC_TERMINAL_ID` is set and forwards each event as JSON on stdin to `simpled hook opencode <event type>` (via Bun).

### Answering a permission

Primary path (plugin hook `permission.ask`, typed in `@opencode-ai/plugin` v1.19.5 with `input: Permission` and `output.status`: `"ask" | "allow" | "deny"`): the plugin sends the `Permission` JSON to `simpled hook opencode permission.ask`, reads stdout, and sets `output.status` to `allow` or `deny` when stdout is `{"status":"allow"}` or `{"status":"deny"}`. Empty stdout leaves `output.status` unchanged so OpenCode shows its own prompt (screen detection still works on the phone).

Fallback (bus event `permission.asked`, only if the hook path did not run): the plugin calls `simpled hook opencode permission.asked`, reads stdout `{"reply":"once"|"always"|"reject"}`, then tries `client.permission?.reply({ requestID, reply, directory })` if present (OpenCode 2 SDK), else `client.postSessionIdPermissionsPermissionId?.({ path: { id: sessionID, permissionID }, body: { response: reply } })` (SDK 1.19.5). If neither API exists, it does nothing.

**Needs live check:** end to end permission on simple-droid with OpenCode 2.2.5 (which path runs, and whether `client.permission.reply` exists on the injected client).

## Transcript

Built from forwarded `message.updated` and `message.part.updated` events, persisted per agent by simpled.

## Context window (phone meter)

Checked 2026-11-09 via `opencode api GET /openapi.json` on this machine (`Session.Message.Assistant.tokens` as `TokenUsage.Info`, `Model.Info.limit.context`). simpled loads `GET /api/session/{id}/message`, takes the latest `type: assistant` item with `tokens`, sets `used` to `input + output + reasoning + cache.read + cache.write`, and `window` from `GET /api/model` for the assistant's `model` (`providerID/id`) or the agent's model label.
