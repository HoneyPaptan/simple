<p align="center">
  <picture><source media="(prefers-color-scheme: dark)" srcset="docs/readme/parts/c-head-dark.png"><img src="docs/readme/parts/c-head-light.png" width="880" alt="Contributing. One person keeps simple in spare time. Here is how to help right now."></picture>
</p>

<p align="center">
  <a href="https://github.com/HoneyPaptan/simple/issues/new/choose"><picture><source media="(prefers-color-scheme: dark)" srcset="docs/readme/parts/c-ways-dark.png"><img src="docs/readme/parts/c-ways-light.png" width="880" alt="Issues are welcome: report a bug or suggest an idea, with your simpled version, OS and agent. Pull requests are closed for now: describe the fix in an issue. Forks are welcome."></picture></a>
</p>

<p align="center">
  <picture><source media="(prefers-color-scheme: dark)" srcset="docs/readme/parts/c-build-dark.png"><img src="docs/readme/parts/c-build-light.png" width="880" alt="Build from source with Go 1.23 or newer, JDK 17 or newer and the Android SDK. scripts/check is the only CI."></picture>
</p>

```sh
git clone https://github.com/HoneyPaptan/simple && cd simple
git submodule update --init
./scripts/check
```

<p align="center">
  <a href="AGENTS.md"><picture><source media="(prefers-color-scheme: dark)" srcset="docs/readme/parts/c-rules-dark.png"><img src="docs/readme/parts/c-rules-light.png" width="880" alt="House rules for collaborators: one change per commit, an 8 MB APK budget, a 10 MB daemon budget, and show it working."></picture></a>
</p>

<details>
<summary>Details for collaborators</summary>

- Set `ANDROID_HOME` or `app/local.properties`. `scripts/check` runs the daemon tests, the app unit tests and lint, a release build and the size budgets.
- Try the daemon against your own agents with `cd daemon && go run ./cmd/simpled serve`, then `simpled pair` and a debug build of the app (`cd app && ./gradlew installDebug`).
- Wire changes start in `docs/protocol.md` and `proto/fixtures/`, then both sides follow.
- No new dependency without a short note in `docs/decisions/`.
- UI text uses no dash characters; use commas, colons or two sentences.
- A new agent needs a transcript parser in `daemon/internal/transcript/`, a kind in `daemon/internal/agents/` and tests with a scrubbed real transcript. Codex support exists but is not tested end to end yet.

</details>
