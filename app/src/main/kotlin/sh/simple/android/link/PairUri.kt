package sh.simple.android.link

data class PairUri(
    val version: Int,
    val hostName: String,
    val addresses: List<String>,
    val fingerprint: String,
    val code: String,
)

fun parsePairUri(text: String): PairUri? {
    val raw = text.filterNot { it.isWhitespace() }
    if (!raw.startsWith("simple://pair")) return null
    val query = raw.substringAfter('?', missingDelimiterValue = "")
    if (query.isEmpty() && !raw.contains('?')) return null
    val params = query.split('&').mapNotNull { part ->
        val eq = part.indexOf('=')
        if (eq <= 0) return@mapNotNull null
        part.substring(0, eq) to part.substring(eq + 1)
    }.toMap()
    val version = params["v"]?.toIntOrNull() ?: return null
    if (version != 1) return null
    val name = params["n"] ?: return null
    val addrs = params["a"]?.split(',')?.filter { it.isNotBlank() } ?: return null
    if (addrs.isEmpty()) return null
    val fp = params["f"]?.let(::fingerprintHex) ?: return null
    val code = params["c"] ?: return null
    if (code.isBlank()) return null
    return PairUri(version, name, addrs, fp, code)
}

private fun fingerprintHex(value: String): String? = when {
    value.matches(Regex("[0-9a-fA-F]{64}")) -> value.lowercase()
    value.matches(Regex("[A-Za-z0-9_-]{43}")) -> java.util.Base64.getUrlDecoder().decode(value).joinToString("") { "%02x".format(it) }
    else -> null
}
