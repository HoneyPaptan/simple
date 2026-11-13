# simple design rules

The first v1 prototype is `docs/design/board.html` (open it in a browser to inspect sizes). The app has moved on since; where the two differ, the app and the rules below win.

## Theme

Dark, pitch black and white. Light mode and the extra palettes (Gruvbox, neutral grey, Catppuccin, One Dark Pro, Cobalt2) come later through the same tokens.

| Token | Value |
|---|---|
| background | `#000000` |
| foreground | `#F8F8F8` |
| muted | `#A1A1A1` |
| faint | foreground at 48 percent |
| card | `#121212` |
| raised | `#262626` |
| line | white 10 percent |
| hairline | white 7.5 percent |
| add | `#2FC58F` |
| remove | `#FF6467` |
| attention (needs you) | `#E9C055` |
| glass fill | white 7.5 percent over blur 28, saturation 1.7, brightness 1.08 |
| glass edge | inner top highlight white 22 percent, inner outline white 9 percent |
| sheet fill | `#2C2C2E` at 66 percent over blur 40 |
| menu and dialog fill | `#303032` at 70 percent over blur 36 |

Fonts: Geist (UI, chat) and Geist Mono (code, screen), bundled. Host fonts can replace them later.

## Sizes (dp)

| Part | Size |
|---|---|
| Screen side margin | 20 (rows, titles, chat text); 12 to 16 for floating glass |
| Status bar clearance | header row top at 58 |
| Header circle button | 44, glass, icon 22 |
| Large title | 22 sp, medium weight, 20 from the left, top at about 126 |
| List row | 54 high, icon column 31, label 17 sp, divider starts under the label |
| Agent row | dot 8 at left 30, title 17 sp, meta 15 sp muted, about 75 high |
| Section label | 15 sp faint, 22 above, 6 below |
| Composer pill | 56 high, 12 from screen edges, 30 from the bottom, inner circle buttons 40 |
| Composer card (focused) | radius 28, padding 10 and 14, handle 36 by 5, context chips 15 sp, text 17 sp line 23, bottom row: plus 34, permission chip 30 high, model text, mic 34, send 34 white |
| Sheet | radius 36 top, handle, close circle 44 at left, title 17 sp semibold centered, search field 44 high pill, section labels 15 sp, rows 52 high |
| Stacked sheet | inset 9 on three sides, radius 40, Done pill 44 high |
| Menu | radius 22, padding 6, item 42 high, icon 19, text 16 sp |
| Dialog | centered, 44 from screen edges, radius 30, buttons 46 high pills side by side |
| Primary button | white pill 50 high, black text 17 sp medium |
| Chat body | 17 sp, line 1.42, headings 22 sp semibold |
| User message | the only bubble: right side, 54 from the left, radius 24, fill `#1C1C1E` |
| Tool work | one line, 16 sp faint: "Explored 14 files, 6 searches" |
| Code card | radius 18, header row: language tag, file name, line range, then the copy button at the right; body Geist Mono 12.5 sp with line numbers |
| Cards (approval, prompt, changes) | radius 18 to 20, fill card, line border, buttons 42 high pills |

## Glass budget

- At most 5 live glass surfaces on screen at once. Header circle buttons, the composer pill or card, and one open sheet or menu each count. A stacked sheet replaces the sheet underneath it.
- Never glass inside glass. A control on a glass surface uses a plain fill (`raised` or a hairline outline), never its own backdrop.
- Never glass inside a lazy list item.
- When Liquid glass is off, every glass surface draws the plain fallback fill used when blur is unavailable, with no blur, lens, or highlight work.

## Performance

Release APKs ship an ART baseline profile sourced from `app/src/main/baseline-prof.txt` (`assets/dexopt/baseline.prof` and `baseline.profm`), installed at runtime via AndroidX ProfileInstaller.

## Placement and motion

- **Menus open on top of the control that opened them**, covering it, aligned to its corner. A control in the lower 60 percent of the screen gets a menu whose bottom edge sits on the control's bottom edge and grows upward; a control near the top gets a menu whose top edge sits on the control's top edge and grows downward. Left or right alignment follows the side the control is on. Scale from 0.55 and fade, from that corner. Never put a menu above or below its trigger.
- **Sheets** rise from the bottom and open at half height by default; drag up for full, down to close. A second sheet stacks on the first (glass on glass through `exportedBackdrop`).
- **Dialogs** scale in at the center over a dim.
- **Autocomplete** (slash commands) sits just above the composer.
- Motion tokens (in `ui/kit/Motion.kt`): hover and fade 120 ms; press 50 ms at scale 0.98; morph 200 ms; layout spring stiffness 440 (menus, dialogs, glass press); conversation spring 224 (message layout); present spring 158 (sheets, navigation push). Menus scale from 0.55 at their anchor, dialogs from 0.96 at the center, sheets slide from the bottom.

## Navigation

Workspaces home (no tab bar) to All Agents or a workspace, to an agent chat. The composer pill on home and lists starts a new agent. The account circle at the top left opens settings. The chat header has back, the title (long press for agent actions), the pane map button and more.
