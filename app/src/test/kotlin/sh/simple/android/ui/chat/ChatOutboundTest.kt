package sh.simple.android.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatOutboundTest {
    @Test
    fun stripImageMarkersKeepsPromptText() {
        assertEquals("What screen is this?", stripImageMarkers("[Image #1]What screen is this?"))
        assertEquals("line two", stripImageMarkers("[Image #1][Image #2]line two"))
    }

    @Test
    fun reconcileClearsWhenTranscriptMatches() {
        val store = ChatOutboundStore()
        store.stage("h", "a", "hello", listOf("/tmp/x.jpg"))
        val cleared = store.reconcile("h", "a", listOf("[Image #1]hello"))
        assertEquals("hello", cleared?.displayText)
        assertNull(store.pending["h:a"])
    }

    @Test
    fun reconcileIgnoresEarlierDuplicateText() {
        val store = ChatOutboundStore()
        store.stage("h", "a", "ok", userLinesBefore = 1)
        assertNull(store.reconcile("h", "a", listOf("ok")))
        val cleared = store.reconcile("h", "a", listOf("ok", "ok"))
        assertEquals("ok", cleared?.displayText)
        assertNull(store.pending["h:a"])
    }

    @Test
    fun displayUserKeyKeepsLocalIdForServerRow() {
        val store = ChatOutboundStore()
        val localId = store.stage("h", "a", "hello")
        store.rememberUserKey("server-1", localId)
        assertEquals(localId, store.displayUserKey("server-1"))
        assertEquals("server-2", store.displayUserKey("server-2"))
    }

    @Test
    fun markSendingAcksDaemon() {
        val store = ChatOutboundStore()
        store.stage("h", "a", "hello")
        assertEquals(false, store.pending["h:a"]?.daemonAcked)
        store.markSending("h", "a")
        assertEquals(true, store.pending["h:a"]?.daemonAcked)
    }

    @Test
    fun retryMintsAFreshCidAndASecondTapDoesNothing() {
        val store = ChatOutboundStore()
        val localId = store.stage("h", "a", "hello")
        store.rememberDelivery("h", "a", "hello", emptyList(), "/tmp/ws")
        val claimed = store.deliveryFor("h", "a")
        assertEquals(localId, claimed?.cid)

        val first = planRetrySend(store, "h", "a", store.pending["h:a"], "/tmp/ws")
        assertNotNull(first)
        assertEquals("hello", first!!.text)
        assertNotEquals(claimed?.cid, first.cid)
        assertEquals(false, store.pending["h:a"]?.daemonAcked)

        assertNull(planRetrySend(store, "h", "a", store.pending["h:a"], "/tmp/ws"))

        store.endRetry("h", "a")
        val fromRemembered = planRetrySend(store, "h", "a", null, "")
        assertNotNull(fromRemembered)
        assertEquals("hello", fromRemembered!!.text)
        assertEquals("/tmp/ws", fromRemembered.workspace)
        assertNotEquals(claimed?.cid, fromRemembered.cid)
    }

    @Test
    fun retryDoesNothingWhileDeliveryIsInFlight() {
        val store = ChatOutboundStore()
        store.stage("h", "a", "hello")
        store.rememberDelivery("h", "a", "hello", emptyList(), "/ws")
        val cid = store.deliveryFor("h", "a")!!.cid
        assertTrue(store.beginDelivery("h", "a", cid))
        assertNull(planRetrySend(store, "h", "a", store.pending["h:a"], "/ws"))
    }

    @Test
    fun waitingForLinkListsAgentForRetry() {
        val store = ChatOutboundStore()
        store.stage("h", "a", "hello")
        store.rememberDelivery("h", "a", "hello", emptyList(), "/tmp/ws")
        store.markWaitingForLink("h", "a")
        assertEquals(listOf("a"), store.agentIdsWithWaitingDelivery("h"))
        store.markSending("h", "a")
        assertEquals(emptyList<String>(), store.agentIdsWithWaitingDelivery("h"))
    }
}
