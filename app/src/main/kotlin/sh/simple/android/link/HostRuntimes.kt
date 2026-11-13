package sh.simple.android.link

import androidx.compose.runtime.mutableStateMapOf
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject

object HostRuntimes {
    private val both = mutableStateMapOf<String, Boolean>()

    fun hasBoth(hostId: String): Boolean = both[hostId] == true

    fun set(hostId: String, hc: String?, herdr: String?) {
        val next = hc != null && herdr != null
        if (both[hostId] != next) both[hostId] = next
    }

    fun applyUpdate(hostId: String, data: JsonElement) {
        val obj = runCatching { data.jsonObject }.getOrNull() ?: return
        val hc = obj["hc"]?.takeIf { it !is JsonNull }
        val herdr = obj["herdr"]?.takeIf { it !is JsonNull }
        val next = hc != null && herdr != null
        if (both[hostId] != next) both[hostId] = next
    }
}
