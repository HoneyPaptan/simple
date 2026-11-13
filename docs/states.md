# Loading, working and empty states

Inventory for the simple Android app. Each row is today versus the shared kit components in `ui/kit/States.kt`.

| Surface | Today | Target |
|---|---|---|
| Home workspaces | Lists as soon as Room has data; no global loading | Cached content only; no blocking spinner |
| Workspace agent list | Empty column when no agents | `EmptyState`: "No agents here yet" + "Start one from the composer below." |
| All Agents | Needs you / Recents with no empty copy | Same `EmptyState` when both sections empty |
| Needs you | Plain "Nothing waiting" text | `EmptyState` with icon: "Nothing needs you" |
| Search | "Nothing matches …" line | `EmptyState`: "No matches for X" |
| Chat before transcript | Centered "Nothing here yet. Send a message to start." | No empty block when outbound pending; otherwise short `EmptyState` or only pending bubble |
| Chat while agent works | `WorkingLine` text row | `WorkingIndicator`: agent logo, calm dots, elapsed after 3 s, tool name when known |
| Tool rows running | Pulsing dot on row | Keep row chrome; dot animation stops when not visible |
| Terminal connecting | Centered "Connecting to the terminal" | `LoadingRow` skeleton briefly, then live PTY |
| Folder picker (`FolderBrowserSheet`) | Plain text while listing | `LoadingRow` lines at top of list |
| Model sheet | Empty list until catalog loads; error line | `LoadingRow` while catalog null; `ErrorState` + Retry for RPC errors |
| Usage | "Loading…" text | `LoadingRow` then content |
| Files browser | "Loading..." text | `LoadingRow`; `ErrorState` on failure |
| Computers / pairing | "Pairing with …" on button area | Status text stays; no full screen spinner |
| Settings webhooks | Error line; "No webhooks yet." | `EmptyState` / `ErrorState` |
| Servers screen | "Loading…" / error text | `LoadingRow` / `ErrorState` |
| Code font settings | "Loading…" while fetching | `LoadingRow` |
| Chat offline header | "Offline" in subtitle | Unchanged |
| Welcome / pair errors | Copy on screen | `ErrorState` where retry applies |

Principles: cached Room or disk content paints on frame one; skeletons only where a list is about to appear; button in flight spinners stay inside buttons only.
