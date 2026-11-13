package sh.simple.android.ui.chat

import sh.simple.android.model.Message

fun workingIndicatorSinceMs(
    messages: List<Message>,
    outbound: OutboundChatBubble?,
    agentStatusUpdatedMs: Long,
    agentWorking: Boolean,
): Long {
    val lastUserTs = messages.asSequence().filter { it.role == "user" }.map { it.ts }.maxOrNull() ?: 0L
    val start = maxOf(lastUserTs, outbound?.stagedAtMs ?: 0L)
    if (start > 0L) return start
    if (agentWorking && agentStatusUpdatedMs > 0L) return agentStatusUpdatedMs
    return System.currentTimeMillis()
}

fun chatAwaitingReply(
    agentWorking: Boolean,
    outbound: OutboundChatBubble?,
    agentStatusUpdatedMs: Long,
): Boolean {
    if (agentWorking) return true
    if (outbound == null) return false
    val agentFinishedAfterSend = outbound.daemonAcked && agentStatusUpdatedMs > outbound.stagedAtMs
    return !agentFinishedAfterSend
}

fun composerShowsStop(agentWorking: Boolean, draft: String, hasAttachments: Boolean): Boolean =
    agentWorking && draft.isBlank() && !hasAttachments
