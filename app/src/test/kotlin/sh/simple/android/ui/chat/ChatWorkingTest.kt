package sh.simple.android.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test
import sh.simple.android.model.Message

class ChatWorkingTest {
    @Test
    fun prefersLastUserMessageTimestamp() {
        val messages = listOf(
            Message("u1", "user", 50_000L, text = "hi"),
            Message("a1", "assistant", 60_000L, text = "hey"),
            Message("u2", "user", 90_000L, text = "go"),
        )
        assertEquals(
            90_000L,
            workingIndicatorSinceMs(messages, null, 80_000L, true),
        )
    }

    @Test
    fun usesOutboundStagedTimeWhilePending() {
        val outbound = OutboundChatBubble("local", "hello", stagedAtMs = 120_000L)
        assertEquals(
            120_000L,
            workingIndicatorSinceMs(emptyList(), outbound, 0L, true),
        )
    }

    @Test
    fun userTimestampBeatsOutboundWhenInTranscript() {
        val messages = listOf(Message("u1", "user", 200_000L, text = "x"))
        val outbound = OutboundChatBubble("local", "x", stagedAtMs = 100_000L)
        assertEquals(
            200_000L,
            workingIndicatorSinceMs(messages, outbound, 0L, true),
        )
    }

    @Test
    fun fallsBackToAgentStatusChange() {
        assertEquals(
            42_000L,
            workingIndicatorSinceMs(emptyList(), null, 42_000L, true),
        )
    }

    @Test
    fun newSendBeatsAnOlderUserMessage() {
        val messages = listOf(Message("u1", "user", 100_000L, text = "x"))
        val outbound = OutboundChatBubble("local", "y", stagedAtMs = 300_000L)
        assertEquals(300_000L, workingIndicatorSinceMs(messages, outbound, 0L, false))
    }

    @Test
    fun pillEndsWhenAgentFinishesAfterAckedSend() {
        val sent = OutboundChatBubble("local", "y", stagedAtMs = 100L, daemonAcked = true)
        assertEquals(false, chatAwaitingReply(false, sent, 200L))
        assertEquals(true, chatAwaitingReply(false, sent, 50L))
        assertEquals(true, chatAwaitingReply(false, sent.copy(daemonAcked = false), 200L))
        assertEquals(true, chatAwaitingReply(true, null, 0L))
        assertEquals(false, chatAwaitingReply(false, sent.copy(daemonAcked = false), 50L, agentFailed = true))
    }

    @Test
    fun retryHidesOnceTheUserTextLands() {
        val outbound = OutboundChatBubble("local", "hello", stagedAtMs = 100L)
        assertEquals(true, sendFailureRetryVisible(true, outbound, emptyList()))
        assertEquals(false, sendFailureRetryVisible(true, outbound, listOf("hello")))
        assertEquals(false, sendFailureRetryVisible(false, outbound, emptyList()))
        assertEquals(
            "Not logged in",
            sendFailureLine(listOf(Message("sf:t1", "event", 1L, text = "Not logged in"))),
        )
    }

    @Test
    fun sendButtonSendsWhenTextIsTypedWhileWorking() {
        org.junit.Assert.assertFalse(composerShowsStop(agentWorking = true, draft = "next step", hasAttachments = false))
        org.junit.Assert.assertFalse(composerShowsStop(agentWorking = true, draft = "", hasAttachments = true))
        org.junit.Assert.assertTrue(composerShowsStop(agentWorking = true, draft = "  ", hasAttachments = false))
        org.junit.Assert.assertFalse(composerShowsStop(agentWorking = false, draft = "", hasAttachments = false))
    }
}
