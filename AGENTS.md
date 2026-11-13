# Agent rules for simple

simple is an Android app (Kotlin, Compose) plus a Go host daemon (`simpled`) for controlling coding agents running in herdr-core on your own computers (Linux and Windows tested). Chat first, built from structured agent data; a terminal mode on the same agent covers TUI only moments (docs/decisions/0005-terminal-mode.md).

## Read first

- `docs/protocol.md`: the phone and daemon contract. Change it first, then both sides.
- `docs/architecture.md`: parts and data flow.
- `docs/design.md`: the UI rules (glass, menus, sheets, components). The first prototype is `docs/design/board.html`.
- `docs/decisions/`: one file per decision.

## Rules

- No code comments except functional ones (license headers, build tags, suppressions).
- Small named functions; reuse before adding; no new dependency without a line in `docs/decisions/`.
- UI text has no dash characters (em dash, en dash, spaced hyphen). Use commas, colons or two sentences.
- Never bind a server to `0.0.0.0` or `::`.
- Never commit `progress/`, APKs, keystores, tokens or third party app code. The pre-commit hook enforces it.
- Every wire shape has a fixture in `proto/fixtures/` that both the Go and Kotlin tests parse.
- `scripts/check` must pass before a change is done. It runs the Go and Kotlin tests, lint, a release build and the size budgets. It uses a remote build box when `SIMPLE_BOX` is set, otherwise it builds locally.
- Commits: one logical change each, plain imperative message, no AI attribution, commit with an explicit pathspec.

## Layout

```
app/       Android app (Gradle, Kotlin, Compose)
daemon/    Go module, simpled
proto/     wire fixtures shared by both test suites
docs/      protocol, architecture, design, decisions
scripts/   check, build, install
```

## README images

Everything in `docs/readme/` except `studio/` is generated; never edit the SVGs or PNGs by hand. Inputs are real app screenshots in `docs/readme/studio/shots/{light,dark}/` taken from the demo host build (1080x2400, emulator night mode matching the theme). Regenerate with `python3 docs/readme/studio/build.py 0.368 0.782` (the two numbers place the tap on Allow inside the notification crop). Needs Pillow with raqm, fontTools and `rsvg-convert`. Agent logos in `studio/icons/` are CC0 from gilbarbara/logos and stay trademarks of their owners.
