package sh.simple.android.store

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TerminalScreenCacheTest {
    @Test
    fun putGetRoundTrip() {
        val raw = byteArrayOf(27, 91, 72, 104, 105)
        TerminalScreenCache.record("h1", "t1", raw, full = true)
        assertArrayEquals(raw, TerminalScreenCache.get("h1", "t1"))
    }

    @Test
    fun deltaAppendsToSnapshot() {
        TerminalScreenCache.record("h1", "t1", byteArrayOf(1), full = true)
        TerminalScreenCache.record("h1", "t1", byteArrayOf(2), full = false)
        assertArrayEquals(byteArrayOf(1, 2), TerminalScreenCache.get("h1", "t1"))
    }

    @Test
    fun missingKeyReturnsNull() {
        assertNull(TerminalScreenCache.get("h9", "missing"))
    }
}
