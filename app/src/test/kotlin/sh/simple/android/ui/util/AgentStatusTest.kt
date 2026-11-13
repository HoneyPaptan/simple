package sh.simple.android.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentStatusTest {
    private val now = 1_700_000_000_000L

    @Test
    fun workingShimmersWithToolTail() {
        val line = agentStatusLine("working", null, "Edit checkout.kt", now, now)
        assertTrue(line.shimmer)
        assertEquals("Working · Edit checkout.kt", line.text)
        assertEquals(" · Edit checkout.kt", line.workingTail)
    }

    @Test
    fun waitingMapsApprovalToCommandPhrase() {
        val line = agentStatusLine("waiting", "approval", null, now, now)
        assertEquals("Wants to run a command", line.text)
        assertEquals(AgentStatusTone.Attention, line.tone)
    }

    @Test
    fun waitingMapsQuestionKind() {
        assertEquals("Has a question", agentWaitingLabel("question"))
        assertEquals("Plan ready", agentWaitingLabel("plan"))
        assertEquals("Waiting for input", agentWaitingLabel("Continue?"))
    }

    @Test
    fun doneShowsRelativeAge() {
        val line = agentStatusLine("done", null, null, now - 720_000, now)
        assertEquals("Done · 12m", line.text)
        assertFalse(line.shimmer)
    }

    @Test
    fun failedUsesRemoveTone() {
        val line = agentStatusLine("failed", null, null, now, now)
        assertEquals("Stopped with an error", line.text)
        assertEquals(AgentStatusTone.Remove, line.tone)
    }

    @Test
    fun idleAndUnknownUseRelativeIdle() {
        val updated = now - 3_600_000
        assertEquals("Idle · 1h", agentStatusLine("idle", null, null, updated, now).text)
        assertEquals("Idle · 1h", agentStatusLine("unknown", null, null, updated, now).text)
    }
}
