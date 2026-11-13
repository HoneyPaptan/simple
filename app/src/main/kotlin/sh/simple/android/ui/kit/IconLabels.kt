package sh.simple.android.ui.kit

import androidx.compose.ui.graphics.vector.ImageVector

fun iconActionLabel(icon: ImageVector): String = when (icon.name.lowercase()) {
    "back" -> "Back"
    "term" -> "Open terminal"
    "more" -> "More options"
    "plus" -> "Attach"
    "mic" -> "Voice input"
    "up" -> "Send"
    "stop" -> "Stop agent"
    "x" -> "Close"
    "search" -> "Search"
    "copy" -> "Copy"
    "trash" -> "Delete"
    "bolt" -> "Test"
    "pencil" -> "Mark up"
    "undo" -> "Undo"
    "redo" -> "Redo"
    "down" -> "Download"
    "folders" -> "Browse folders"
    "folder" -> "Folder"
    "branch" -> "Branch"
    "image" -> "Image"
    "camera" -> "Camera"
    "qr" -> "Scan code"
    "refresh" -> "Refresh"
    "eye" -> "Preview"
    "grid" -> "Pane map"
    "server" -> "Servers"
    "sliders" -> "Model"
    "bell" -> "Notifications"
    "shield" -> "Mode"
    "person" -> "Profile"
    "pin" -> "Pin"
    "file" -> "File"
    else -> icon.name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}

fun brandIconLabel(icon: ImageVector): String = when (icon.name.lowercase()) {
    "claude" -> "Claude"
    "codex" -> "Codex"
    "cursor" -> "Cursor"
    "opencode" -> "OpenCode"
    else -> icon.name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}
