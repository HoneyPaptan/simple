# Security review (v1 build)

Last recheck: 2026-11-09 (T24, APIs since 2026-11-05).

## Threat model

The phone and `simpled` talk over your tailnet with pinned TLS. Anyone who can reach an open port could try to pair, guess preview URLs, or abuse bulk file routes. The design assumes you trust your tailnet boundary and treat each paired phone as a full operator on that host (agents, files, dev server previews). There is no multi tenant isolation on one daemon.

## Baseline checks (unchanged)

| Check | Result |
|---|---|
| HTTP routes authenticated or scoped as in `docs/protocol.md` | **Pass.** `POST /v1/pair` uses a one time pairing code. `GET /v1/ws`, bulk `/v1/file`, and JSON RPC over the socket require `Authorization: Bearer` and a stored device token hash. Preview uses a random path token plus a separate `simple_preview` session cookie; neither is forwarded to the dev server. |
| No listener on `0.0.0.0` or `::` | **Pass.** `listenAddrs` binds loopback, tailnet addresses, and optional LAN IPs only. Repo grep in `scripts/check` and `TestListenAddrsNeverWildcard` in `daemon/internal/api/addrs_test.go`. |
| Secret files mode `0600`, config dirs `0700` | **Pass.** `EnsureDirs` uses `0700`. `config.json`, `devices.json`, `key.pem`, and generated `cert.pem` use `0600` via `atomicWrite`. On Windows the same files live under the user profile; standard ACLs restrict them to the logged in user (not a substitute for explicit DACL hardening in v1). |
| Device tokens never logged | **Pass.** `log.Print` in `daemon/` is listen or serve errors only, no token or prompt fields. |
| Device token lookup constant time | **Pass.** `FindDeviceByToken` compares SHA-256 digests with `crypto/subtle.ConstantTimeCompare`. `replace_token` on pair uses `FindDeviceByTokenSHA256` with the same compare (fixed 2026-11-09; was `EqualFold`). |
| Release manifest | **Pass** on the build box merged release manifest: `allowBackup=false`, no `debuggable`, no `usesCleartextTraffic` (explicit `false` on `<application>`). `android:exported="true"` only on `MainActivity` (launcher and `simple://pair`) and on AndroidX `ProfileInstallReceiver` with `android:permission="android.permission.DUMP"`. App components (`LinkForegroundService`, boot receiver, inbox answer receiver, FileProvider) are `exported="false"`. |
| WebView preview | **Pass.** `PreviewScreen` does not call `addJavascriptInterface`. File access from file URLs is disabled. JavaScript stays on for dev server UIs; content is loaded only over pinned HTTPS to the daemon origin with cookie gated preview proxying. |

## APIs added or extended since 2026-11-05

| Surface | Auth | Input bounds | Paths / scope | Logs | Result |
|---|---|---|---|---|---|
| `POST /v1/pair` + `replace_token` | Pairing code | Code rate limit unchanged | Revokes only a device in this host's `devices.json` when `replace_token` matches a stored `token_sha256`; unknown hash ignored (`TestPairReplaceToken`) | No token in logs | **Pass** (constant-time hash match added 2026-11-09) |
| `PUT`/`GET /v1/file` (attachments) | Bearer | 20 MiB, image types only | `files.upload` path under `<workspace>/.simple/attachments/`; `PutAttachment` rejects paths outside that dir (`attachments_test.go`) | No body logged | **Pass** |
| `files.upload` | WS + hello | Allowed ext set | Same attachment dir rules | — | **Pass** |
| `files.search` | WS + hello | Default 30 results, max 20000 paths walked, limit capped to `maxSearchFiles` | `cwd` must resolve under user home (`ResolvePathUnderHome`, `TestSearchRejectsOutsideHome`, `TestResolvePathUnderHomeRejectsOutside`); relative `..` rejected at resolve | — | **Pass** |
| `files.list` / `files.read` / `files.write` | WS + hello | Read max 8 MiB, binary rejected | Absolute paths only (`ResolvePath`); paired device is full host operator per threat model | — | **Pass** (documented scope) |
| `agent.commands` | WS + hello | Agent id must exist | Reads slash lists from agent kind only | No prompt text logged | **Pass** |
| `agent.configure` / OpenCode interrupt | WS + hello | 5 s OpenCode API timeout | Session id from daemon-owned agent record only | HC errors not logged with secrets | **Pass** |
| `agent.transcript` / `agent.watch` / `usage.summary` | WS + hello | Transcript page limit; usage days capped at 90 | Reads under user home for usage scan | — | **Pass** |
| `term.open` / `term.resize` / `term.close` | WS + hello | One PTY stream per socket; 32 KiB read chunks | Terminal id must exist in hc | Terminal bytes not logged | **Pass** |
| `servers.list` / `servers.kill` | WS + hello | Kill only pids from current list | Loopback listeners for daemon uid | — | **Pass** |
| `preview.open` + preview proxy | Bearer then cookie | Port must be in current server list; 10 min TTL | Proxy to `127.0.0.1` only; no cred forward | — | **Pass** |
| `webhooks.*` | WS + hello | HTTPS URLs validated; sample POST 5 s timeout | Outbound only to configured URL | Webhook secret not logged on send | **Pass** |
| `fonts.list` / `fonts.get` + font bulk GET | WS + hello | — | Download only paths from last `fonts.list` cache | — | **Pass** |
| `devices.list` / `devices.revoke` | WS + hello | Revoke by device id | Local store only | — | **Pass** |
| `git.status` / `git.diff` / `git.commit` | WS + hello | Git subprocess timeouts | `cwd` via `files.ResolvePath` | — | **Pass** |

## Follow ups

- Consider whether AndroidX baseline profile `ProfileInstallReceiver` should stay exported or be overridden in a future manifest merge rule.
- Run periodic manifest review when dependencies change.
