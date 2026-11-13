package sh.simple.android.ui.composer

import sh.simple.android.ui.util.compactForUpload
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sh.simple.android.link.Hosts
import sh.simple.android.link.RemoteGateway
import sh.simple.android.store.SimpleDatabase
import sh.simple.android.store.TokenVault

fun attachmentFromLocalPath(path: String): ComposerAttachment? {
    val file = File(path)
    if (!file.isFile) return null
    val mime = when (file.extension.lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        else -> return null
    }
    return ComposerAttachment(file.name, path, mime)
}

suspend fun uploadAttachmentLocalPaths(
    hosts: Hosts,
    vault: TokenVault,
    db: SimpleDatabase,
    hostId: String,
    workspace: String,
    localPaths: List<String>,
): List<String> {
    val items = localPaths.mapNotNull { attachmentFromLocalPath(it) }
    return uploadComposerAttachments(hosts, vault, db, hostId, workspace, items)
}

suspend fun uploadComposerAttachments(
    hosts: Hosts,
    vault: TokenVault,
    db: SimpleDatabase,
    hostId: String,
    workspace: String,
    items: List<ComposerAttachment>,
): List<String> = withContext(Dispatchers.IO) {
    if (items.isEmpty() || workspace.isBlank()) return@withContext emptyList()
    val host = db.hosts().listAll().firstOrNull { it.id == hostId } ?: return@withContext emptyList()
    val out = ArrayList<String>(items.size)
    for (item in items) {
        if (extForMime(item.mime) == null) continue
        val image = compactForUpload(item.localPath, item.mime)
        val ext = extForMime(image.mime) ?: continue
        val path = RemoteGateway.uploadAttachment(hosts, vault, host, workspace, image.bytes, image.mime, ext)
        out.add(path)
    }
    out
}
