from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[3]
PARTS = "docs/readme/parts"
REPO = "https://github.com/HoneyPaptan/simple"


def pic(src, alt, width, dark=None, href=None):
    dark = dark or src.replace("-light.", "-dark.")
    tag = (f'<picture><source media="(prefers-color-scheme: dark)" srcset="{dark}">'
           f'<img src="{src}" width="{width}" alt="{alt}"></picture>')
    return f'<a href="{href}">{tag}</a>' if href else tag


def part(name, alt, href=None):
    w = Image.open(ROOT / PARTS / f"{name}-light.png").width // 2
    return pic(f"{PARTS}/{name}-light.png", alt, w, href=href)


def center(*items, sep="\n  "):
    return '<p align="center">\n  ' + sep.join(items) + "\n</p>"


def anchor(name):
    return f'<a name="{name}"></a>'


def code(lang, text):
    return f"```{lang}\n{text.strip()}\n```"


def details(summary, body):
    return f"<details>\n<summary>{summary}</summary>\n\n{body.strip()}\n\n</details>"


def card(name, alt):
    return pic(f"docs/readme/card-{name}-light.png", alt, 410)


README = [
    center(pic("docs/readme/hero-light.svg", "A computer runs three coding agents in herdr. One asks to run the tests, the phone shows the request, Allow is tapped, and the agent carries on.", 880, dark="docs/readme/hero-dark.svg")),
    center(part("r-lockup", "simple. Your coding agents, in your pocket. Open source, no cloud, no account, no telemetry.")),
    center(
        part("pill-install", "Install", "#install"),
        part("pill-how", "How it works", "#how-it-works"),
        part("pill-security", "Security", "SECURITY.md"),
        part("pill-issues", "Issues", f"{REPO}/issues"),
        part("pill-side", "Side project", "#side-project"),
    ),
    center(
        card("start", "Start agents from the phone: pick a folder, an agent and a model, then type."),
        card("approve", "Answer from the notification: approvals and questions with Allow and Deny buttons."),
    ),
    center(
        card("chat", "A real chat: turns, tool calls and diffs read from each agent's own transcript."),
        card("review", "Review and commit: see what changed per file, read the diff, commit and push."),
    ),
    center(part("r-tiles", "Works offline: sends wait on the phone and go out once. Real terminal with a key bar and a Nerd Font. Voice dictation that never leaves the phone.")),
    center(pic("docs/readme/screens-light.png", "Four screens from the app: Today with every agent, a chat with tool calls, the changes view with a diff, and workspaces.", 880)),
    center(part("r-agents", "Runs the agents you already use: Claude Code, OpenCode and Cursor are tested, Codex is next.")),
    anchor("install"),
    center(part("r-install", "Install in two lines. On the computer, run the install script, then simpled pair. On the phone, install simple.apk from the latest release and scan the code. Both need Tailscale. macOS builds ship but are not tested yet.", f"{REPO}/releases/latest")),
    code("sh", f"""
# Linux and macOS
curl -fsSL {REPO}/releases/latest/download/install.sh | sh
simpled pair
"""),
    code("powershell", f"""
# Windows
irm {REPO}/releases/latest/download/install.ps1 | iex
simpled pair
"""),
    details("Pair over home Wi-Fi without Tailscale", """
Set `"lan": true` in simpled's `config.json`, run `simpled install` to restart it, then run `simpled pair` again. `simpled status` shows whether the daemon runs, `simpled devices` lists paired phones and `simpled devices rm <id>` revokes one. Run the install command again to update.
"""),
    anchor("how-it-works"),
    center(part("r-how", "How it works: the phone talks to simpled over pinned TLS on your tailnet. simpled is one Go binary that drives herdr panes, which run your agents. No relay, no telemetry, loopback and tailnet only, a token per phone.", "docs/architecture.md")),
    center(part("r-numbers", "Numbers: 7.1 MB Android app, 9.3 MB daemon, 297 ms cold start on a 2018 phone, zero servers of ours.", "docs/perf.md")),
    anchor("side-project"),
    center(part("r-side", "A side project, built by one person in spare time. Replies can take a few days.")),
    center(
        part("pill-issues-welcome", "Issues welcome", f"{REPO}/issues/new/choose"),
        part("pill-prs-closed", "Pull requests closed for now", "CONTRIBUTING.md"),
        part("pill-security-private", "Security reports stay private", "SECURITY.md"),
    ),
    center(part("r-foot", "Apache 2.0. Fonts under OFL 1.1. Agent logos belong to their owners.", "docs/licenses.md")),
]

SECURITY = [
    center(part("s-head", "Security. A paired phone can run your agents and touch your files, so reports matter.")),
    center(part("s-report", "Found something? Report a vulnerability privately from the Security tab. Never a public issue. Include steps to reproduce and the app and simpled versions. Answers within a week.", f"{REPO}/security/advisories/new")),
    center(part("s-model", "What protects the link: a pinned certificate from the QR, a one time code that expires in 5 minutes, a token per phone you can revoke, and a daemon that listens on loopback and your tailnet only.", "docs/security.md")),
    center(part("s-versions", "Only the latest release gets fixes. Update by running the install command again.")),
]

CONTRIBUTING = [
    center(part("c-head", "Contributing. One person keeps simple in spare time. Here is how to help right now.")),
    center(part("c-ways", "Issues are welcome: report a bug or suggest an idea, with your simpled version, OS and agent. Pull requests are closed for now: describe the fix in an issue. Forks are welcome.", f"{REPO}/issues/new/choose")),
    center(part("c-build", "Build from source with Go 1.23 or newer, JDK 17 or newer and the Android SDK. scripts/check is the only CI.")),
    code("sh", f"""
git clone {REPO} && cd simple
git submodule update --init
./scripts/check
"""),
    center(part("c-rules", "House rules for collaborators: one change per commit, an 8 MB APK budget, a 10 MB daemon budget, and show it working.", "AGENTS.md")),
    details("Details for collaborators", """
- Set `ANDROID_HOME` or `app/local.properties`. `scripts/check` runs the daemon tests, the app unit tests and lint, a release build and the size budgets.
- Try the daemon against your own agents with `cd daemon && go run ./cmd/simpled serve`, then `simpled pair` and a debug build of the app (`cd app && ./gradlew installDebug`).
- Wire changes start in `docs/protocol.md` and `proto/fixtures/`, then both sides follow.
- No new dependency without a short note in `docs/decisions/`.
- UI text uses no dash characters; use commas, colons or two sentences.
- A new agent needs a transcript parser in `daemon/internal/transcript/`, a kind in `daemon/internal/agents/` and tests with a scrubbed real transcript. Codex support exists but is not tested end to end yet.
"""),
]


def write(name, blocks):
    (ROOT / name).write_text("\n\n".join(blocks) + "\n")


if __name__ == "__main__":
    write("README.md", README)
    write("SECURITY.md", SECURITY)
    write("CONTRIBUTING.md", CONTRIBUTING)
