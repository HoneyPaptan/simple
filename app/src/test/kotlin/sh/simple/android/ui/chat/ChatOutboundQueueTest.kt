package sh.simple.android.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatOutboundQueueTest {
    @Test
    fun secondSendKeepsTheFirstAsQueued() {
        val store = ChatOutboundStore()
        store.stage("h", "a", "first", userLinesBefore = 2)
        store.stage("h", "a", "second", userLinesBefore = 2)
        assertEquals("second", store.pending["h:a"]?.displayText)
        assertEquals(listOf("first"), store.queued["h:a"]?.map { it.displayText })
    }

    @Test
    fun reconcileDropsQueuedOnceTranscriptShowsThem() {
        val store = ChatOutboundStore()
        store.stage("h", "a", "first", userLinesBefore = 1)
        store.stage("h", "a", "second", userLinesBefore = 1)
        store.reconcile("h", "a", listOf("old", "first"))
        assertEquals(null, store.queued["h:a"])
        assertEquals("second", store.pending["h:a"]?.displayText)
    }

    @Test
    fun sendWhileAgentAlreadyWorkingIsQueued() {
        assertTrue(outboundIsQueued(agentWorking = true, agentStatusUpdatedMs = 1_000L, stagedAtMs = 2_000L))
        assertFalse(outboundIsQueued(agentWorking = true, agentStatusUpdatedMs = 3_000L, stagedAtMs = 2_000L))
        assertFalse(outboundIsQueued(agentWorking = false, agentStatusUpdatedMs = 1_000L, stagedAtMs = 2_000L))
    }
}
