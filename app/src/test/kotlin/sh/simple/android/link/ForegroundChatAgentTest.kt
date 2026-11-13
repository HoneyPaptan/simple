package sh.simple.android.link

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundChatAgentTest {
    @Test
    fun tracksVisibleChatAgent() {
        ForegroundChatAgent.clear()
        assertFalse(ForegroundChatAgent.isVisible("h1", "a1"))
        ForegroundChatAgent.set("h1", "a1")
        assertTrue(ForegroundChatAgent.isVisible("h1", "a1"))
        assertFalse(ForegroundChatAgent.isVisible("h1", "a2"))
        ForegroundChatAgent.clear()
        assertFalse(ForegroundChatAgent.isVisible("h1", "a1"))
    }
}
