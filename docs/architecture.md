# Architecture

```
Phone (app/)                                   Linux host
┌──────────────────────────────┐   TLS, pinned  ┌────────────────────────────────────┐
│ UI (Compose, glass)          │   WebSocket    │ simpled (daemon/)                  │
│  └ reads the local store     │◄──────────────►│  ├ api: pairing, ws, bulk routes   │
│ Store (Room)                 │   per host     │  ├ hc client (unix socket JSON-RPC)│
│  └ fed by HostLink per host  │                │  ├ agents: kind detect, transcripts│
│ HostLink (OkHttp ws)         │                │  ├ hooks: approvals and questions  │
│ Background service (alerts)  │                │  ├ detectors: screen prompt cards  │
└──────────────────────────────┘                │  └ inbox, event ring, webhooks     │
                                                │ herdr-core (hc): agent terminals   │
                                                └────────────────────────────────────┘
```

## Daemon packages (`daemon/internal/`)

| Package | Job |
|---|---|
| `config` | paths, config.json, certificate, devices |
| `hc` | herdr-core client: `hello`, calls, `events.subscribe` stream, reconnect |
| `api` | HTTPS server, pairing, WebSocket sessions, bulk routes, auth |
| `hub` | event sequence, ring of 4096 events, epoch, fan out to sockets |
| `agents` | agent kinds, start commands, models and modes per kind, state merge from hc plus hooks |
| `transcript` | one parser per agent kind, file watching, Message conversion |
| `hooks` | `simpled hook` command side and daemon side, pending answers |
| `detect` | screen text detectors for prompt cards |
| `inbox` | items, persistence in `~/.local/state/simpled/inbox.json` |
| `gitx`, `files`, `servers`, `usage`, `fonts`, `webhooks` | the smaller methods |
| `control` | local unix socket for `simpled pair`, `devices`, `hook` |

## App modules (`app/src/main/java/sh/simple/android/`)

| Package | Job |
|---|---|
| `link` | HostLink: one WebSocket per host, hello and replay, request ids, backoff reconnect |
| `store` | Room database: hosts, agents, items, messages, workspaces; DAO flows |
| `model` | wire types (kotlinx.serialization) |
| `ui/glass` | glass primitives on the backdrop library, appearance settings |
| `ui/kit` | header circles, rows, sheets, menus, dialogs, composer, cards |
| `ui/screens` | one file per screen on the board |
| `alerts` | foreground service, notifications with actions |
| `pair` | QR scanner, pairing call, pinned TLS |
