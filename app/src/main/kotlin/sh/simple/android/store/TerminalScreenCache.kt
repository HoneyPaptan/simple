package sh.simple.android.store

import java.util.concurrent.ConcurrentHashMap

object TerminalScreenCache {
    private const val maxBytes = 256 * 1024
    private val screens = ConcurrentHashMap<String, ByteArray>()

    fun key(hostId: String, agentId: String): String = "$hostId:$agentId"

    fun get(hostId: String, agentId: String): ByteArray? = screens[key(hostId, agentId)]?.copyOf()

    fun record(hostId: String, agentId: String, bytes: ByteArray, full: Boolean) {
        if (bytes.isEmpty()) return
        val k = key(hostId, agentId)
        val next = if (full) {
            bytes
        } else {
            val prev = screens[k]
            if (prev == null) bytes else prev + bytes
        }
        screens[k] = if (next.size > maxBytes) {
            next.copyOfRange(next.size - maxBytes, next.size)
        } else {
            next.copyOf()
        }
    }
}
