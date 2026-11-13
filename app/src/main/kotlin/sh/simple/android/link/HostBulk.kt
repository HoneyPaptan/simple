package sh.simple.android.link

import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import sh.simple.android.store.HostEntity
import sh.simple.android.store.TokenVault

object HostBulk {
    suspend fun getFile(
        host: HostEntity,
        vault: TokenVault,
        path: String,
    ): ByteArray = withContext(Dispatchers.IO) {
        val token = vault.decrypt(host.tokenCipher)
        val encoded = URLEncoder.encode(path, Charsets.UTF_8.name())
        var last: Exception? = null
        for (addr in host.addresses.split(',').filter { it.isNotBlank() }) {
            val base = if (addr.contains("://")) addr else "https://$addr"
            val url = base.trimEnd('/') + "/v1/file?path=$encoded"
            val client = PinnedTls.client(host.fingerprint)
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .build()
            try {
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) error("HTTP ${resp.code}")
                    return@withContext resp.body?.bytes() ?: error("empty")
                }
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: LinkDroppedException("download failed")
    }

    suspend fun putFile(
        host: HostEntity,
        vault: TokenVault,
        path: String,
        bytes: ByteArray,
        contentType: String,
    ) = withContext(Dispatchers.IO) {
        val token = vault.decrypt(host.tokenCipher)
        val encoded = URLEncoder.encode(path, Charsets.UTF_8.name())
        var last: Exception? = null
        for (addr in host.addresses.split(',').filter { it.isNotBlank() }) {
            val base = if (addr.contains("://")) addr else "https://$addr"
            val url = base.trimEnd('/') + "/v1/file?path=$encoded"
            val client = PinnedTls.client(host.fingerprint)
            val body = bytes.toRequestBody(contentType.toMediaType())
            val request = Request.Builder()
                .url(url)
                .put(body)
                .header("Authorization", "Bearer $token")
                .build()
            try {
                client.newCall(request).execute().use { resp ->
                    if (resp.isSuccessful || resp.code == 204) return@withContext
                    error("HTTP ${resp.code}")
                }
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: LinkDroppedException("upload failed")
    }
}
