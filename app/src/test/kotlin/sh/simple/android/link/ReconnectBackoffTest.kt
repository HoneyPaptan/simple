package sh.simple.android.link

import org.junit.Assert.assertTrue
import org.junit.Test

class ReconnectBackoffTest {
    @Test
    fun doublesUntilCap() {
        val b = ReconnectBackoff(initialMs = 250, maxMs = 8000, random = kotlin.random.Random(0))
        val first = b.nextDelayMs()
        assertTrue(first in 250..312)
        val second = b.nextDelayMs()
        assertTrue(second >= 500)
        b.reset()
        assertTrue(b.nextDelayMs() in 250..312)
    }
}
