package sh.simple.android.link

import java.io.IOException
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import sh.simple.android.model.PairDevice
import sh.simple.android.model.PairError
import sh.simple.android.model.PairRequest
import sh.simple.android.store.HostEntity
import sh.simple.android.model.PairResponse
import sh.simple.android.model.WireJson
import sh.simple.android.store.TokenVault

data class PairResult(
    val hostId: String,
    val hostName: String,
    val token: String,
    val fingerprint: String,
    val addresses: List<String>,
)

class PairingException(val kind: String, message: String? = null) : IOException(message ?: kind)

object Pairing {
    private val jsonMedia = "application/json".toMediaType()

    suspend fun pair(
        uri: PairUri,
        deviceName: String,
        deviceModel: String,
        vault: TokenVault? = null,
        hostRowForFingerprint: (suspend (String) -> HostEntity?)? = null,
    ): PairResult = withContext(Dispatchers.IO) {
        val replace = replaceTokenHash(uri.fingerprint, vault, hostRowForFingerprint)
        val body = WireJson.encodeToString(
            PairRequest(uri.code, PairDevice(deviceName, deviceModel), replace),
        ).toRequestBody(jsonMedia)
        var lastError: Throwable? = null
        var pinMismatch = false
        for (addr in uri.addresses) {
            val base = if (addr.startsWith("http")) addr else "https://$addr"
            val url = base.trimEnd('/') + "/v1/pair"
            val client = PinnedTls.client(uri.fingerprint).newBuilder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
            val request = Request.Builder().url(url).post(body).build()
            try {
                client.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        val parsed = WireJson.decodeFromString<PairResponse>(text)
                        return@withContext PairResult(
                            hostId = parsed.host.id,
                            hostName = parsed.host.name,
                            token = parsed.token,
                            fingerprint = uri.fingerprint,
                            addresses = uri.addresses,
                        )
                    }
                    val err = runCatching { WireJson.decodeFromString<PairError>(text) }.getOrNull()
                    throw PairingException(err?.kind ?: "http_${response.code}")
                }
            } catch (e: PairingException) {
                throw e
            } catch (e: Throwable) {
                if (e.hasCause<CertificateException>()) pinMismatch = true
                lastError = e
            }
        }
        if (pinMismatch) throw PairingException("cert_mismatch")
        throw PairingException("unreachable")
    }
}

private inline fun <reified T : Throwable> Throwable.hasCause(): Boolean =
    generateSequence(this) { it.cause }.take(8).any { it is T }

private fun tokenSha256Hex(token: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    return digest.digest(token.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

private suspend fun replaceTokenHash(
    fingerprint: String,
    vault: TokenVault?,
    lookup: (suspend (String) -> HostEntity?)?,
): String? {
    if (vault == null || lookup == null) return null
    val row = lookup(fingerprint.lowercase()) ?: lookup(fingerprint) ?: return null
    if (!row.fingerprint.equals(fingerprint, ignoreCase = true)) return null
    return tokenSha256Hex(vault.decrypt(row.tokenCipher))
}
