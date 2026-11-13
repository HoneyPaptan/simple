# Accessibility audit (2026-11-10)

TalkBack, Switch Access, hardware keyboard, and system font scaling. WCAG 2.2 AA where it applies to a native Android app.

Legend: **T37** = fixed in task T37 (this branch). **T35** = home list rows, chat header chrome, `States.kt`, `Icons.kt`. **T36** = `ui/theme/*`, appearance settings. **T34** = `ui/pane/*`, daemon panes API.

## Home

| Area | TalkBack today | Gaps | Owner |
|------|----------------|------|-------|
| Agent rows | Title, subtitle from row text | Status text may be color only (**T35**) | T35 |
| Search, new agent | Icon buttons via `HeaderBar` / pills | — | **T37** labels + 48 dp |
| Empty / loading | `EmptyState`, `LoadingRow` titles | Decorative row icons unlabeled (**T35** `States.kt`) | T35 |

## Chat

| Area | TalkBack today | Gaps | Owner |
|------|----------------|------|-------|
| Header | `CircleButton` labels from icon name ("Back", "Term", "More") | Title icon (agent brand) unnamed; subtitle not grouped (**T35** header work) | **T37** brand label; T35 grouping |
| Transcript user bubble | Text only | No merged "You said" node; image thumbs unnamed | **T37** |
| Assistant markdown | Paragraph text | Long messages not landmarked | **T37** headings in cards only |
| Tool groups / rows | Row text when expanded | Collapsed row is separate controls; status dot color only; no "Edited file, done" phrase | **T37** |
| Thinking row | Text when expanded | Same as tools | **T37** |
| Working row | "Working" text + timer suffix every second | Not a live region; elapsed tick is noisy (**T35** shares `WorkingIndicator` in `States.kt`) | **T37** live region wrapper in `ChatScreen` |
| Approval / question / plan cards | Button titles | No custom actions on card root | **T37** |
| Code / diff cards | Code content | Copy control icon only | **T37** |
| Composer pill | Whole pill is one button | Plus, mic/stop icons silent; mic when working should read "Stop agent" | **T37** |
| Composer card | Text field | Send/stop, plus, mic, mode/model chips: icon only or unlabeled; model row small target | **T37** |
| Inbox peek terminal card | Terminal text | Live dot color only | **T37** |

## Terminal (`AgentScreen`)

| Area | TalkBack today | Gaps | Owner |
|------|----------------|------|-------|
| Header | Back | — | **T37** |
| Terminal surface | `AndroidView` emulator | Expected: user explores buffer separately | — |
| Key bar | Text labels on keys | Targets 36 dp tall | **T37** 48 dp |
| Ctrl toggle | "ctrl" | No checked state | **T37** toggle semantics |

## Composer sheets (new agent, folder, model, attachments)

| Area | TalkBack today | Gaps | Owner |
|------|----------------|------|-------|
| `GlassSheet` | Title text, Close | Title not `heading()`; focus not moved on open | **T37** |
| Folder browser rows | Row labels | — | **T37** touch targets |
| Model sheet | Row labels | Provider rows small chevron hits (**T34** list) | T37 where in `ModelSheet.kt` |
| Image markup | Some `CircleButton` labels | Color swatches unnamed (decorative OK) | **T37** |

## Settings (not appearance)

| Area | TalkBack today | Gaps | Owner |
|------|----------------|------|-------|
| Settings hub rows | Row title + chevron | Section titles not headings | **T37** |
| Voice, webhooks, keys, terminal theme | Mix of labeled trash/copy | Webhook test/trash icon only; toggles OK | **T37** |
| Code font, transfer | Row navigation | — | **T37** |

## Appearance

| Area | TalkBack today | Gaps | Owner |
|------|----------------|------|-------|
| Theme, accent, density | Controls with text | Focus ring from theme token (**T36**) | T36 |

## Pairing and welcome

| Area | TalkBack today | Gaps | Owner |
|------|----------------|------|-------|
| Pair QR / paste | Instructions + buttons | Copy row partially unlabeled icon | **T37** |
| Welcome | Copy control | Icon decorative only, row has no role | **T37** |

## Changes, usage, files, servers

| Area | TalkBack today | Gaps | Owner |
|------|----------------|------|-------|
| Changes list | File rows | Push toggle color + text | **T37** |
| Usage | Tables | Numeric only, readable | OK |
| Files | Path rows | — | **T37** targets |
| Servers | Stop icon labeled | Open row vs stop | **T37** |

## Shared kit

| Component | TalkBack today | Gaps | Owner |
|-----------|----------------|------|-------|
| `CircleButton` | `contentDescription` when passed | Default 44 dp | **T37** 48 dp + labels |
| `CenterDialog` | Title + buttons | Title not focused on open | **T37** |
| `GlassSheet` | Title, Close | Same as above | **T37** |
| `AnchoredMenu` | Menu items | Selected state on check items | **T37** |
| `PrimaryButton` / `SecondaryButton` | Title | Secondary 42 dp min height | **T37** 48 dp |
| `Toggle` | Switch role | 44 dp row | **T37** 48 dp |

## Large font scale (1.3x display + font)

| Area | Risk | Owner |
|------|------|-------|
| Composer field | Fixed max height 6 lines scales with text | **T37** OK; chips row may clip | **T37** FlowRow |
| GlassChip | `maxLines = 1` | **T37** wrap |
| Card subtitles | `maxLines` caps | **T37** relax where needed |
| Chat header title | `maxLines = 1` ellipsis | **T35** header |
| Tool / thinking rows | Ellipsis | **T37** allow 2 lines |

## Hardware keyboard

| Area | Today | Owner |
|------|-------|-------|
| Composer Enter / Shift+Enter | Implemented in `ComposerCardFields` | **T37** |
| Composer Escape | Closes suggestions or composer | **T37** |
| Sheets Escape | Back only | **T37** add Escape |
| Focus ring | No visible ring on kit buttons | **T37** `Tokens.focusRing` |
| Tab order | Visual order mostly; terminal key bar scrolls horizontally | **T37** focusable keys |

## Color only

| Signal | Mitigation | Owner |
|--------|------------|-------|
| Tool running dot | Phrase includes verb + detail | **T37** |
| Diff +/- counts | Spoken with numbers in text | OK |
| Failed agent banner | Text explains | OK |
| Agent list status (**T35**) | — | T35 |

## T37 item map

1. This document.
2. Icon `contentDescription` on icon buttons; decorative `null`; brand icons use agent name.
3. 48 by 48 dp click targets (padding, not larger glyphs).
4. Headings, merged transcript nodes, tool phrases, card custom actions, working live region, sheet/dialog focus + Back.
5. Large font clipping fixes in owned screens.
6. Tab/focus ring, Enter/Escape on sheets.
7. Compose semantics tests (composer send, approval card, dialog title; icon label guard).
