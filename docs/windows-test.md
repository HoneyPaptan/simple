# Windows test checklist (Honey)

1. Install herdr-core: `powershell -c "irm https://releases.herdr.dev/core/install.ps1 | iex"`
2. Install simpled from the latest release `install.ps1`
3. Run `simpled pair` and complete pairing on the phone
4. Start Claude from the phone
5. Approve a permission from a notification
6. Open terminal mode for a hc terminal
7. Attach an image to a message
8. Confirm Claude or Cursor hooks fire (`HC_TERMINAL_ID` present)
9. Reboot and confirm the scheduled task restarts simpled
