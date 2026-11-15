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
    agentFailed: Boolean = false,
): Boolean {
    if (agentFailed) return false
    if (agentWorking) return true
    if (outbound == null) return false
    val agentFinishedAfterSend = outbound.daemonAcked && agentStatusUpdatedMs > outbound.stagedAtMs
    return !agentFinishedAfterSend
}

fun sendFailureRetryVisible(
    agentFailed: Boolean,
    outbound: OutboundChatBubble?,
    transcriptUserLines: List<String>,
): Boolean {
    if (!agentFailed || outbound == null) return false
    val want = normalizeUserMatchText(outbound.displayText)
    if (want.isEmpty()) return false
    return transcriptUserLines.none { normalizeUserMatchText(it) == want }
}

fun sendFailureLine(messages: List<Message>): String {
    val text = messages.lastOrNull { it.role == "event" && it.id.startsWith("sf:") }?.text?.trim().orEmpty()
    return text.ifEmpty { "This agent stopped with an error." }
}

fun composerShowsStop(agentWorking: Boolean, draft: String, hasAttachments: Boolean): Boolean =
    agentWorking && draft.isBlank() && !hasAttachments
