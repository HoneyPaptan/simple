package sh.simple.android.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PairUriTest {
    @Test
    fun parsesValidUri() {
        val uri = parsePairUri(
            "simple://pair?v=1&n=simple&a=100.64.0.1:47420&f=" + "a".repeat(64) + "&c=abc123",
        )
        requireNotNull(uri)
        assertEquals(1, uri.version)
        assertEquals("simple", uri.hostName)
        assertEquals(listOf("100.64.0.1:47420"), uri.addresses)
        assertEquals("a".repeat(64), uri.fingerprint)
        assertEquals("abc123", uri.code)
    }

    @Test
    fun rejectsBadScheme() {
        assertNull(parsePairUri("https://pair?v=1&n=x&a=1.2.3.4:1&f=${"b".repeat(64)}&c=x"))
    }

    @Test
    fun rejectsBadFingerprint() {
        assertNull(parsePairUri("simple://pair?v=1&n=x&a=1.2.3.4:1&f=short&c=x"))
    }

    @Test
    fun readsShortFingerprintAsHex() {
        val uri = parsePairUri("simple://pair?v=1&n=box&a=100.64.0.1:47420&f=q6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6s&c=abc123")
        assertEquals("ab".repeat(32), requireNotNull(uri).fingerprint)
    }

    @Test
    fun readsLinkCopiedAcrossWrappedLines() {
        val wrapped = "  simple://pair?v=1&n=box&a=100.64.0.1:47420&f=q6urq6\n  urq6urq6urq6urq6urq6urq6urq6urq6urq6s&c=abc123\n"
        val uri = requireNotNull(parsePairUri(wrapped))
        assertEquals("abc123", uri.code)
        assertEquals(listOf("100.64.0.1:47420"), uri.addresses)
    }
}
