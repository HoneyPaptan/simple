package sh.simple.android.ui.usage

import org.junit.Assert.assertEquals
import org.junit.Test

class UsageFormatTest {
    @Test
    fun formatTokensCompact() {
        assertEquals("999", formatTokens(999))
        assertEquals("1.5k", formatTokens(1500))
        assertEquals("2.0M", formatTokens(2_000_000))
    }
}
