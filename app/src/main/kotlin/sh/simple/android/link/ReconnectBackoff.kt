package sh.simple.android.link

import kotlin.random.Random

class ReconnectBackoff(
    private val initialMs: Long = 250,
    private val maxMs: Long = 8000,
    private val random: Random = Random.Default,
) {
    private var currentMs: Long = initialMs

    fun nextDelayMs(): Long {
        val jitter = if (currentMs > 0) random.nextLong(0, currentMs / 4 + 1) else 0L
        val delay = currentMs + jitter
        currentMs = (currentMs * 2).coerceAtMost(maxMs)
        return delay
    }

    fun reset() {
        currentMs = initialMs
    }

    fun peekBaseMs(): Long = currentMs
}
