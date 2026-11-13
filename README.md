<p align="center">
  <picture><source media="(prefers-color-scheme: dark)" srcset="docs/readme/hero-dark.svg"><img src="docs/readme/hero-light.svg" width="880" alt="A computer runs three coding agents in herdr. One asks to run the tests, the phone shows the request, Allow is tapped, and the agent carries on."></picture>
</p>

<p align="center">
  <picture><source media="(prefers-color-scheme: dark)" srcset="docs/readme/parts/r-lockup-dark.png"><img src="docs/readme/parts/r-lockup-light.png" width="880" alt="simple. Your coding agents, in your pocket. Open source, no cloud, no account, no telemetry."></picture>
</p>

<p align="center">
  <a href="#install"><picture><source media="(prefers-color-scheme: dark)" srcset="docs/readme/parts/pill-install-dark.png"><img src="docs/readme/parts/pill-install-light.png" width="129" alt="Install"></picture></a>
  <a href="#how-it-works"><picture><source media="(prefers-color-scheme: dark)" srcset="docs/readme/parts/pill-how-dark.png"><img src="docs/readme/parts/pill-how-light.png" width="176" alt="How it works"></picture></a>
  <a href="SECURITY.md"><picture><source media="(prefers-color-scheme: dark)" srcset="docs/readme/parts/pill-security-dark.png"><img src="docs/readme/parts/pill-security-light.png" width="144" alt="Security"></picture></a>
  <a href="https://github.com/HoneyPaptan/simple/issues"><picture><source media="(prefers-color-scheme: dark)" srcset="docs/readme/parts/pill-issues-dark.png"><img src="docs/readme/parts/pill-issues-light.png" width="130" alt="Issues"></picture></a>
  <a href="#side-project"><picture><source media="(prefers-color-scheme: dark)" srcset="docs/readme/parts/pill-side-dark.png"><img src="docs/readme/parts/pill-side-light.png" width="169" alt="Side project"></picture></a>
</p>

<p align="center">
  <picture><source media="(prefers-color-scheme: dark)" srcset="docs/readme/card-start-dark.png"><img src="docs/readme/card-start-light.png" width="410" alt="Start agents from the phone: pick a folder, an agent and a model, then type."></picture>
  <picture><source media="(prefers-color-scheme: dark)" srcset="docs/readme/card-approve-dark.png"><img src="docs/readme/card-approve-light.png" width="410" alt="Answer from the notification: approvals and questions with Allow and Deny buttons."></picture>
</p>

<p align="center">
  <picture><source media="(prefers-color-scheme: dark)" srcset="docs/readme/card-chat-dark.png"><img src="docs/readme/card-chat-light.png" width="410" alt="A real chat: turns, tool calls and diffs read from each agent's own transcript."></picture>
  <picture><source media="(prefers-color-scheme: dark)" srcset="docs/readme/card-review-dark.png"><img src="docs/readme/card-review-light.png" width="410" alt="Review and commit: see what changed per file, read the diff, commit and push."></picture>
</p>

<p align="center">
  <picture><source media="(prefers-color-scheme: dark)" srcset="docs/readme/parts/r-tiles-dark.png"><img src="docs/readme/parts/r-tiles-light.png" width="880" alt="Works offline: sends wait on the phone and go out once. Real terminal with a key bar and a Nerd Font. Voice dictation that never leaves the phone."></picture>
</p>

<p align="center">
  <picture><source media="(prefers-color-scheme: dark)" srcset="docs/readme/screens-dark.png"><img src="docs/readme/screens-light.png" width="880" alt="Four screens from the app: Today with every agent, a chat with tool calls, the changes view with a diff, and workspaces."></picture>
</p>

<p align="center">
  <picture><source media="(prefers-color-scheme: dark)" srcset="docs/readme/parts/r-agents-dark.png"><img src="docs/readme/parts/r-agents-light.png" width="880" alt="Runs the agents you already use: Claude Code, OpenCode and Cursor are tested, Codex is next."></picture>
</p>

<a name="install"></a>

<p align="center">
  <a href="https://github.com/HoneyPaptan/simple/releases/latest"><picture><source media="(prefers-color-scheme: dark)" srcset="docs/readme/parts/r-install-dark.png"><img src="docs/readme/parts/r-install-light.png" width="880" alt="Install in two lines. On the computer, run the install script, then simpled pair. On the phone, install simple.apk from the latest release and scan the code. Both need Tailscale. macOS builds ship but are not tested yet."></picture></a>
</p>

```sh
# Linux and macOS
curl -fsSL https://github.com/HoneyPaptan/simple/releases/latest/download/install.sh | sh
simpled pair
```

```powershell
# Windows
irm https://github.com/HoneyPaptan/simple/releases/latest/download/install.ps1 | iex
simpled pair
```

<details>
<summary>Pair over home Wi-Fi without Tailscale</summary>

Set `"lan": true` in simpled's `config.json`, run `simpled install` to restart it, then run `simpled pair` again. `simpled status` shows whether the daemon runs, `simpled devices` lists paired phones and `simpled devices rm <id>` revokes one. Run the install command again to update.

</details>

<a name="how-it-works"></a>

<p align="center">
  <a href="docs/architecture.md"><picture><source media="(prefers-color-scheme: dark)" srcset="docs/readme/parts/r-how-dark.png"><img src="docs/readme/parts/r-how-light.png" width="880" alt="How it works: the phone talks to simpled over pinned TLS on your tailnet. simpled is one Go binary that drives herdr panes, which run your agents. No relay, no telemetry, loopback and tailnet only, a token per phone."></picture></a>
</p>

<p align="center">
  <a href="docs/perf.md"><picture><source media="(prefers-color-scheme: dark)" srcset="docs/readme/parts/r-numbers-dark.png"><img src="docs/readme/parts/r-numbers-light.png" width="880" alt="Numbers: 7.1 MB Android app, 9.3 MB daemon, 297 ms cold start on a 2018 phone, zero servers of ours."></picture></a>
</p>

<a name="side-project"></a>

<p align="center">
  <picture><source media="(prefers-color-scheme: dark)" srcset="docs/readme/parts/r-side-dark.png"><img src="docs/readme/parts/r-side-light.png" width="880" alt="A side project, built by one person in spare time. Replies can take a few days."></picture>
</p>

<p align="center">
  <a href="https://github.com/HoneyPaptan/simple/issues/new/choose"><picture><source media="(prefers-color-scheme: dark)" srcset="docs/readme/parts/pill-issues-welcome-dark.png"><img src="docs/readme/parts/pill-issues-welcome-light.png" width="198" alt="Issues welcome"></picture></a>
  <a href="CONTRIBUTING.md"><picture><source media="(prefers-color-scheme: dark)" srcset="docs/readme/parts/pill-prs-closed-dark.png"><img src="docs/readme/parts/pill-prs-closed-light.png" width="286" alt="Pull requests closed for now"></picture></a>
  <a href="SECURITY.md"><picture><source media="(prefers-color-scheme: dark)" srcset="docs/readme/parts/pill-security-private-dark.png"><img src="docs/readme/parts/pill-security-private-light.png" width="286" alt="Security reports stay private"></picture></a>
</p>

<p align="center">
  <a href="docs/licenses.md"><picture><source media="(prefers-color-scheme: dark)" srcset="docs/readme/parts/r-foot-dark.png"><img src="docs/readme/parts/r-foot-light.png" width="880" alt="Apache 2.0. Fonts under OFL 1.1. Agent logos belong to their owners."></picture></a>
</p>
