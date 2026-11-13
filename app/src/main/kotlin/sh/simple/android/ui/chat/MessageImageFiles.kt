package sh.simple.android.ui.chat

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sh.simple.android.store.MessageImageDao
import sh.simple.android.store.MessageImageEntity

private fun pendingMessageId(localId: String) = "pending:$localId"

suspend fun persistMessageImages(
    context: Context,
    dao: MessageImageDao,
    hostId: String,
    agentId: String,
    localId: String,
    sourcePaths: List<String>,
) = withContext(Dispatchers.IO) {
    if (sourcePaths.isEmpty()) return@withContext
    val messageId = pendingMessageId(localId)
    val rows = ArrayList<MessageImageEntity>(sourcePaths.size)
    sourcePaths.forEachIndexed { index, src ->
        val stored = storeImageFile(context, hostId, agentId, messageId, index, src) ?: return@forEachIndexed
        rows += MessageImageEntity(hostId, agentId, messageId, index + 1, stored)
    }
    if (rows.isNotEmpty()) dao.upsertAll(rows)
}

suspend fun rebindPendingMessageImages(
    dao: MessageImageDao,
    hostId: String,
    agentId: String,
    localId: String,
    messageId: String,
) = withContext(Dispatchers.IO) {
    dao.rebindMessageId(hostId, agentId, pendingMessageId(localId), messageId)
}

private fun storeImageFile(
    context: Context,
    hostId: String,
    agentId: String,
    messageId: String,
    slotIndex: Int,
    sourcePath: String,
): String? {
    val src = File(sourcePath)
    if (!src.isFile) return null
    val ext = src.name.substringAfterLast('.', "jpg")
    val dir = File(context.filesDir, "message-images/$hostId/$agentId/$messageId").apply { mkdirs() }
    val dest = File(dir, "${slotIndex + 1}.$ext")
    return runCatching {
        src.copyTo(dest, overwrite = true)
        dest.absolutePath
    }.getOrNull()
}
