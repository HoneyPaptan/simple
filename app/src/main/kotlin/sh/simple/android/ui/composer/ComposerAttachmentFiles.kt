package sh.simple.android.ui.composer

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

data class ComposerAttachment(
    val id: String,
    val localPath: String,
    val mime: String,
    val markup: Boolean = false,
)

fun extForMime(mime: String): String? = when (mime.lowercase()) {
    "image/jpeg" -> ".jpg"
    "image/png" -> ".png"
    "image/webp" -> ".webp"
    "image/gif" -> ".gif"
    else -> null
}

fun importAttachment(context: Context, uri: Uri, mimeHint: String? = null): ComposerAttachment? {
    val resolver = context.contentResolver
    val mime = mimeHint?.takeIf { it.isNotBlank() }
        ?: resolver.getType(uri)
        ?: return null
    val ext = extForMime(mime) ?: return null
    val dir = File(context.cacheDir, "composer-attach").apply { mkdirs() }
    val dest = File(dir, "${UUID.randomUUID()}$ext")
    resolver.openInputStream(uri)?.use { input ->
        dest.outputStream().use { out -> input.copyTo(out) }
    } ?: return null
    return ComposerAttachment(UUID.randomUUID().toString(), dest.absolutePath, mime)
}

fun newCameraCaptureFile(context: Context): File {
    val dir = File(context.cacheDir, "composer-attach").apply { mkdirs() }
    return File(dir, "${UUID.randomUUID()}.jpg")
}
