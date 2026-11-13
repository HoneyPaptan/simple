package sh.simple.android.ui.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalReconnectHintTest {
    @Test
    fun idleScreenAfterFirstFrameIsNotReconnecting() {
        assertFalse(terminalNeedsReconnectHint(linkLive = true, attached = true, lastFrameAtMs = 1_000L))
    }

    @Test
    fun attachedWithoutAnyFrameHints() {
        assertTrue(terminalNeedsReconnectHint(linkLive = true, attached = true, lastFrameAtMs = 0L))
    }

    @Test
    fun linkDownHints() {
        assertTrue(terminalNeedsReconnectHint(linkLive = false, attached = true, lastFrameAtMs = 1_000L))
    }
}
