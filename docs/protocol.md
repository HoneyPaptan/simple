# simple protocol v1

The contract between the phone app and `simpled`. Both sides follow this file; change it first, then the code.

## Transport

- `simpled` listens on TCP port `47420` (config `port`) on `127.0.0.1` and on each tailnet address of the host (any IPv4 in `100.64.0.0/10` or IPv6 in `fd7a:115c:a1e0::/48`). Never `0.0.0.0` or `::`. A LAN address only when `config.json` has `"lan": true`.
- TLS 1.3 only, with a self signed ECDSA P-256 certificate generated on first start (`~/.config/simpled/cert.pem`, `key.pem`, ten years). The phone trusts exactly the certificate whose SHA-256 fingerprint (of the DER bytes, lowercase hex) came in the pairing QR. No CA, no hostname check.
- Every request except `POST /v1/pair` carries `Authorization: Bearer <token>`. Tokens are 32 random bytes, base64url without padding. The daemon stores only their SHA-256.

## Pairing

1. `simpled pair` asks the running daemon (over the local control socket `$XDG_RUNTIME_DIR/simpled.sock`) for a pairing code: 16 random bytes, base64url, valid 5 minutes, single use.
2. It prints a QR of the pairing URI (terminal half-block characters, one character per module), a hint line, then the same URI wrapped to the QR width so it can be copied into Enter code. The app drops all whitespace before parsing, so the wrapped copy works.
   `simple://pair?v=1&n=<host name>&a=<addr:port>,<addr:port>&f=<fingerprint>&c=<code>`
   `f` is the SHA-256 certificate fingerprint as unpadded base64url (43 characters); the app also accepts 64 hex characters.
   Addresses: IPv4 tailnet addresses (plus LAN addresses when `lan` is on); IPv6 tailnet addresses only when no IPv4 one exists. Loopback is never included.
3. The app connects to each address in order with the pinned fingerprint and sends
   `POST /v1/pair` `{"code":"...","device":{"name":"Pixel","model":"Pixel 8"},"replace_token":"<hex sha-256 of prior token>"}`.
   `replace_token` is optional. When present and it matches a stored `token_sha256`, that device is revoked after the new one is added; the new device keeps the old name. Unknown `replace_token` values are ignored.
   Response `200 {"device_id":"d_...","token":"...","host":{"id":"h_...","name":"simple","version":"0.1.0"}}`.
   Errors: `401 {"kind":"bad_code"}` (wrong or expired; 5 wrong codes lock pairing for 1 minute), `409 {"kind":"used"}`.
4. Manual entry: the app accepts the same URI pasted as text.

Devices live in `~/.config/simpled/devices.json` (`id`, `name`, `model`, `token_sha256`, `created`, `last_seen`). `simpled devices` prints a table (newest first); `--json` emits the list JSON. `simpled devices rm <id>` revokes one (its open sockets close at once).

## Main channel: WebSocket `GET /v1/ws`

One WebSocket per paired host carries everything except bulk bytes. Text frames, one JSON object each. WebSocket ping every 15 s from both sides; the daemon closes a socket whose pong does not arrive within 30 s.

| Direction | Shape |
|---|---|
| App to daemon, request | `{"id":7,"m":"agent.send","p":{...}}` |
| Daemon to app, result | `{"id":7,"r":{...}}` |
| Daemon to app, error | `{"id":7,"e":{"kind":"not_found","msg":"no agent t9"}}` |
| Daemon to app, event | `{"ev":"agent.update","seq":1042,"d":{...}}` |

Terminal frames carry raw terminal bytes outside the event sequence (never replayed): daemon to app `{"t":"s1","b":"<base64>"}` in order, optional `"full":true` when the app should replace its canvas with that chunk (first frame after attach, after resize, and after `term.snap`), then `{"t":"s1","done":true}` when the attach ends; app to daemon `{"t":"s1","b":"<base64>"}` for typed keys, with no reply. ctrl+\\ detaches `hc attach`, so the app never sends byte 0x1c.

Error kinds: `bad_request`, `not_found`, `hc_unavailable`, `agent_unsupported`, `denied`, `timeout`, `internal`, `binary`, `too_large`, `push_failed` (`conflict` is only `409` on `POST /v1/pair` when the code was already used).

### hello (always first)

Request `{"m":"hello","p":{"app":"0.1.0","epoch":"e_...","since":1040}}` (`epoch` and `since` omitted on first connect).

Result:
```json
{"host":{"id":"h_..","name":"simple","version":"0.1.0","epoch":"e_..","hc":"0.1.17","herdr":"0.9.1","linger":true},
 "seq":1042,
 "replay":true,
 "snapshot":null}
```
- `epoch` changes every time the daemon starts. If the app's `epoch` matches and `since` is still in the daemon's ring of the last 4096 events, `replay` is true and the events after `since` follow at once as normal event frames.
- Otherwise `replay` is false and `snapshot` is `{"agents":[Agent],"inbox":[Item],"workspaces":[Workspace]}`; the app replaces its stored state for this host with it.
- `hc` is null when herdr-core is not running; `herdr` is null when the Herdr TUI server is not reachable; `linger` false means the app shows the linger hint.

## Types

```text
Agent {
  id          string   hc terminal address, e.g. "t4", or "h:w1:p1" for Herdr panes
  runtime     string   "hc" | "herdr"
  kind        string   "claude" | "cursor" | "opencode" | "codex" | "fx" | "shell" | "other"
  title       string   user title, else the agent's session title, else the command
  cwd         string
  workspace   string   absolute path of the workspace (git root of cwd, else cwd)
  status      string   "working" | "idle" | "waiting" | "done" | "failed" | "unknown"
  waiting     string?  short reason when status is waiting ("Approval: go test", "Question")
  model       string?
  effort      string?
  mode        string?  "ask" | "auto" | "full"
  started_ms  int
  updated_ms  int
  pinned      bool
  unread      bool
  branch      string?
  session     string?  the agent's own session id when known
  context     {used int, window int}?  tokens in the model context window; omitted when unknown
}

Item {                         inbox entry
  id          string   "i_..."
  agent       string   Agent.id
  kind        string   "approval" | "question" | "plan" | "prompt" | "done" | "failed"
  title       string
  body        string?  markdown
  command     string?  approval: the command or tool input shown in mono
  tool        string?  approval: tool name
  options     [Option]?  question, plan and prompt choices
  detector    string?  prompt: "choice" | "yesno" | "continue" | "link" | "input" | "error"
  link        string?  prompt link
  created_ms  int
  read        bool
  resolved    bool
}

Option { id string, label string, detail string? }

Workspace { path string, name string, branch string?, dirty bool, agents int, pinned bool, updated_ms int }

Pane {
  id          string   same as Agent.id when an agent pane, else hc terminal address or "h:<pane_id>"
  runtime     string   "hc" | "herdr"
  workspace   string   absolute workspace path
  tab         string?  herdr tab id; omitted on hc
  title       string
  kind        string   agent kind or "shell"
  status      string   "working" | "idle" | "waiting" | "done" | "failed" | "unknown"
  rect        {x, y, width, height}?  optional herdr layout rect
}

PaneTab { id string, label string, panes:[Pane] }

Message {                      one chat entry from a transcript
  id     string   stable within the session
  role   string   "user" | "assistant" | "tool" | "system" | "thinking" (model reasoning text) | "event" (turn marker such as "Stopped")
  ts     int
  text   string?  markdown
  tool   Tool?
  images [string]?  blob ids
}

Tool { name string, summary string, input string?, output string?, status "running"|"ok"|"error"|"denied", diff string? }
```

## Methods

| Method | Params | Result |
|---|---|---|
| `agents.list` | | `{agents:[Agent]}` |
| `agent.start` | `{kind, cwd, prompt?, model?, effort?, mode, runtime?: "hc"|"herdr", worktree?:{branch}}` | `{agent:Agent}` |
| `agent.send` | `{id, text, images?:[blob], cid?}` | `{}` (sent once the pane is quiet; a repeated `cid` from the same phone within 15 min answers `{}` without sending again, a failed send frees its `cid`). A send the agent cannot take sets status `failed` and appends an `event` message with the reason. The event id is `sf:<agent id>`. An `sf:` event with empty text clears that failure. |
| `agent.keys` | `{id, keys:[string]}` (hc key names: `enter`, `esc`, `up`, `down`, `left`, `right`, `tab`, `ctrl+c`, `y`, `n`, `1`...) | `{}` |
| `agent.interrupt` | `{id}` | `{status}` |
| `agent.kill` | `{id}` | `{}` |
| `agent.remove` | `{id}` | `{}` |
| `agent.update` | `{id, title?, pinned?, unread?}` | `{agent}` |
| `agent.screen` | `{id, lines?}` | `{text, rows, cols}` |
| `agent.watch` | `{id, screen:bool}` | `{}`; while on, `screen` events for that agent at most every 300 ms when the text changes. One watched screen per socket. |
| `term.open` | `{id, cols, rows}` | `{sid}`; runs `hc attach --history 1000 <id>` in a PTY of that size. One stream per socket: opening another closes the previous one. Bytes then flow as terminal frames. |
| `term.resize` | `{sid, cols, rows}` | `{}`; the next frame is marked `full`. |
| `term.snap` | `{sid}` | `{}`; nudges a full redraw, the next frame is marked `full`. |
| `term.close` | `{sid}` | `{}` |
| `agent.transcript` | `{id, before?, limit?}` | `{messages:[Message], before?:string}` newest last; `before` pages backwards |
| `agent.models` | `{kind}` | `{models:[{id,label,efforts:[string]}], modes:[string]}` |
| `agent.commands` | `{id}` | `{commands:[{name, description, hint?}]}` |
| `agent.kinds` | | `{kinds:{claude:bool,codex:bool,cursor:bool,opencode:bool,fx:bool,shell:bool,gemini:bool,qwen:bool,copilot:bool,amp:bool,goose:bool,crush:bool,aider:bool,kimi:bool}}` (`shell` is always true; others reflect whether the binary is on the host PATH) |
| `agent.configure` | `{id, model?, effort?, mode?}` | `{agent}` (Claude: `/model` in its TUI; OpenCode: its session API; mode is a label every kind reads; see docs/agents) |
| `inbox.list` | | `{items:[Item]}` |
| `inbox.answer` | `{id, decision:"allow"\|"deny"\|"always", option?, text?}` | `{}` |
| `inbox.read` | `{ids:[string]}` | `{}` |
| `workspaces.list` | | `{workspaces:[Workspace]}` |
| `panes.list` | `{workspace}` | `{tabs:[PaneTab]}` one implicit tab with empty `id` on hc when herdr tabs are not used |
| `panes.peek` | `{ids:[string], lines?}` | `{peeks:[{id, text}]}` at most 8 ids, 20 lines each |
| `panes.spawn` | `{workspace}` | `{id}` new shell pane in the workspace |
| `files.list` | `{path}` | `{entries:[{name, dir, size, mtime_ms}]}` |
| `files.read` | `{path, offset?, limit?}` | `{text, lines, truncated}` (text files up to 8 MiB) |
| `files.write` | `{path, text}` | `{}` |
| `files.mkdir` | `{path, name}` | `{}`; `name` has no `/`, `..`, or NUL; parent must resolve under home after symlinks; mode `0755`; errors: `exists`, `invalid_name`, `outside_home` |
| `files.upload` | `{workspace, ext}` (`.jpg`, `.jpeg`, `.png`, `.webp`, `.gif`) | `{path}` absolute path under `<workspace>/.simple/attachments/<random>.<ext>`; body goes up with bulk `PUT /v1/file?path=` (image types only, max 20 MiB) |
| `files.search` | `{cwd, query, limit?}` | `{paths:[string]}` relative paths under `cwd` |
| `git.status` | `{cwd}` | `{branch, ahead, behind, files:[{path, status, add, del}]}` |
| `git.diff` | `{cwd, path?}` | `{diff}` unified text |
| `git.commit` | `{cwd, message, push}` | `{commit}` |
| `servers.list` | | `{servers:[{port, pid, command, cwd}]}` listeners owned by the daemon uid; excludes simpled and hc |
| `servers.kill` | `{pid}` | `{}` SIGTERM when pid is in the current list |
| `preview.open` | `{port}` | `{path, expires_ms, session}` one time `/v1/preview/<token>/` for that port, valid 10 minutes |
| `usage.summary` | `{days?}` default 7 | `{days, today, period, daily:[{day, input, output, cache_read, cache_write, cost_usd?}], agents:[{id, kind, title, has_data, total}]}` tokens from Claude JSONL `message.usage` and Codex `token_usage_record`; cost only when the transcript records it |
| `webhooks.list` | | `{webhooks:[{id, url, kinds}]}` |
| `webhooks.set` | `{id?, url, kinds}` | `{webhook, secret}` secret only when creating |
| `webhooks.remove` | `{id}` | `{}` |
| `webhooks.test` | `{id}` | `{}`; errors: `webhook_unreachable`, `timeout`, `webhook_status` (message is the HTTP status code) |
| `fonts.list` | | `{fonts:[{path, family}]}` |
| `fonts.get` | `{path}` | `{}` then download with bulk `GET /v1/file?path=` |
| `devices.list` | | `{devices:[{id, name, model, created, last_seen}]}` |
| `devices.revoke` | `{id}` | `{}` |

Webhook POST body: `{event, host, agent, workspace, title, body?, created_ms}` where `event` is the inbox item kind (or `webhook.test`), header `X-Simple-Signature: sha256=<hex HMAC SHA256 of the raw body with the secret>`. 5 s timeout, no retries, redirects are not followed. Only `https://` URLs, plus `http://127.0.0.1` and `http://localhost`.

Preview: `GET /v1/preview/<token>/<path>` proxies to `http://127.0.0.1:<port>/<path>`. `preview.open` (over the pinned socket) returns the path and a separate `session` secret; the app puts the session into the WebView as the `simple_preview` cookie (Secure, HttpOnly) and the device bearer never enters the WebView. Every preview request needs that cookie, including absolute paths like `/assets/app.js` on the daemon origin, which proxy to the same port. Both are valid 10 minutes for one port. Neither `Authorization` nor that cookie is forwarded to the dev server.

## Events

| Event | Data |
|---|---|
| `agent.update` | `Agent` (full object) |
| `agent.removed` | `{id}` |
| `inbox.add` | `Item` |
| `inbox.update` | `Item` |
| `transcript.append` | `{id, messages:[Message]}` (new or changed entries; same `id` replaces) |
| `screen` | `{id, text, rows, cols}` |
| `workspace.update` | `Workspace` |
| `host.update` | `{hc, herdr?, linger}` |

## Bulk HTTP (bearer auth)

| Route | Use |
|---|---|
| `GET /v1/blob/<id>` | images from transcripts and uploads |
| `POST /v1/blob` | upload raw body (`Content-Type` set), returns `{id}`; the daemon stores it under `~/.cache/simpled/blobs/` and gives agents a file path |
| `GET /v1/file?path=` | raw file download (fonts and workspace attachment files) |
| `PUT /v1/file?path=` | upload attachment bytes for a path from `files.upload` (204, bearer auth, `Content-Type` must be an allowed image type) |
| `GET /v1/font/<id>` | font file |
| `ANY /v1/preview/<token>/...` | reverse proxy to `http://127.0.0.1:<port>` for an open preview grant (WebSocket upgrade included) |

## Hooks (host only)

Agents' hooks run `simpled hook <kind> <event>`. That command reads the hook JSON on stdin, adds `HC_TERMINAL_ID` from its environment, and calls the daemon over the local control socket. For approvals and questions it waits for the phone's answer (up to the agent's own hook timeout minus 5 s), then prints the agent's expected hook output. With no daemon running it exits 0 with no output so the agent falls back to its own prompt.
