package sh.simple.android.ui.home

import sh.simple.android.model.Message
import sh.simple.android.model.WireJson
import sh.simple.android.store.MessageEntity
import sh.simple.android.store.MessageImageEntity

fun latestAssistantPreview(rows: List<MessageEntity>): String {
    for (index in rows.indices.reversed()) {
        val row = rows[index]
        if (row.role != "assistant") continue
        val msg = runCatching { WireJson.decodeFromString<Message>(row.json) }.getOrNull() ?: continue
        val text = msg.text?.trim().orEmpty()
        if (text.isNotEmpty()) return plainPreview(text)
    }
    return ""
}

fun tilePreviewImagePath(
    messages: List<MessageEntity>,
    stored: List<MessageImageEntity>,
): String? {
    if (messages.isEmpty() || stored.isEmpty()) return null
    val latestAssistant = messages.lastOrNull { it.role == "assistant" } ?: return null
    return stored
        .filter { it.messageId == latestAssistant.id }
        .minByOrNull { it.slot }
        ?.localPath
        ?.takeIf { it.isNotBlank() }
}

private val codeFence = Regex("```[^\\n]*\\n?")
private val link = Regex("\\[([^\\]]+)]\\([^)]*\\)")
private val emphasis = Regex("\\*\\*|__|`|~~|(?<!\\w)[*_](?=\\S)|(?<=\\S)[*_](?!\\w)")
private val linePrefix = Regex("(?m)^\\s*(#{1,6}\\s+|[-*+]\\s+|>\\s?|\\d+\\.\\s+)")
private val spaces = Regex("\\s+")

fun plainPreview(markdown: String): String =
    markdown
        .replace(codeFence, "")
        .replace(link, "$1")
        .replace(linePrefix, "")
        .replace(emphasis, "")
        .replace(spaces, " ")
        .trim()
