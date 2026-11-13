package sh.simple.android.ui.inbox

import sh.simple.android.model.Item

fun approvalCardSubtitle(item: Item): String {
    val tool = item.tool.orEmpty()
    if (tool.isNotEmpty() && !tool.equals(item.title, ignoreCase = true)) return tool
    val body = item.body.orEmpty()
    if (body.isNotEmpty() && !body.equals(item.title, ignoreCase = true)) return body
    return ""
}

fun approvalCardCommand(item: Item): String {
    val cmd = item.command.orEmpty()
    if (cmd.isNotEmpty()) return cmd
    return item.body.orEmpty()
}
