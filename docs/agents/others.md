# Terminal agent CLIs

Launch uses `terminal.run` with `cwd` set to the workspace (or worktree), same as shell. Mode Full adds the skip approvals flag below when one exists; other modes are labels only. Never run a real model session from tests.

| Kind | Binary | Launch argv | Full mode (skip approvals) | Doc |
|---|---|---|---|---|
| Cursor | `cursor-agent` | `cursor-agent --trust` | `--force` | https://cursor.com/docs/agent/cli |
| Gemini | `gemini` | `gemini --skip-trust` | `--approval-mode yolo` | https://github.com/google-gemini/gemini-cli |
| Qwen | `qwen` | `qwen` | `--approval-mode yolo` | https://qwenlm.github.io/qwen-code-docs/en/users/quickstart/ |
| Copilot | `copilot` | `copilot` | `--allow-all` | https://docs.github.com/en/copilot/reference/copilot-cli-reference/cli-command-reference |
| Amp | `amp` | `amp` | `--dangerously-allow-all` | https://ampcode.com/docs/cli |
| Goose | `goose` | `goose session` | `GOOSE_MODE=auto` in env | https://github.com/block/goose/blob/main/documentation/docs/guides/goose-cli-commands.md |
| Crush | `crush` | `crush` | `--yolo` | https://github.com/charmbracelet/crush |
| Aider | `aider` | `aider` | `--yes-always` | https://aider.chat/docs/config/options.html |
| Kimi | `kimi` | `kimi` | `--auto` | https://moonshotai.github.io/kimi-code/en/reference/kimi-command |

Screen prompts use the generic `detect` package (yes or no, numbered choice, continue, link). Structured chat is not wired for these kinds yet.
