package sh.simple.android.ui.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatPeekTest {
    @Test
    fun plainProgramsPeek() {
        assertTrue(chatShowsScreenPeek("shell"))
        assertTrue(chatShowsScreenPeek("fx"))
        assertTrue(chatShowsScreenPeek("gemini"))
    }

    @Test
    fun structuredAgentsNeverShowTerminalText() {
        for (kind in listOf("claude", "codex", "opencode", "cursor")) assertFalse(chatShowsScreenPeek(kind))
    }
}
