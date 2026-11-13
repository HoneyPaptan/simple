package sh.simple.android.ui.chat

private val structuredChatKinds = setOf("claude", "codex", "opencode", "cursor")

fun chatShowsScreenPeek(kind: String?): Boolean =
    kind != null && kind !in structuredChatKinds
