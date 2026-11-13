package sh.simple.android.ui.chat

import androidx.compose.runtime.mutableStateMapOf
import java.util.UUID

data class OutboundChatBubble(
    val localId: String,
    val displayText: String,
    val imagePaths: List<String> = emptyList(),
    val userLinesBefore: Int = 0,
    val waitingForLink: Boolean = false,
    val stagedAtMs: Long = System.currentTimeMillis(),
    val daemonAcked: Boolean = false,
)

class ChatOutboundStore {
    val pending = mutableStateMapOf<String, OutboundChatBubble>()
    val queued = mutableStateMapOf<String, List<OutboundChatBubble>>()
    private val deliveries = mutableStateMapOf<String, QueuedFollowUpDelivery>()
    private val inFlight = HashSet<String>()

    private fun agentKey(hostId: String, agentId: String) = "$hostId:$agentId"

    fun stage(
        hostId: String,
        agentId: String,
        text: String,
        imagePaths: List<String> = emptyList(),
        userLinesBefore: Int = 0,
    ): String {
        val localId = UUID.randomUUID().toString()
        val key = agentKey(hostId, agentId)
        pending[key]?.let { earlier -> queued[key] = queued[key].orEmpty() + earlier }
        pending[key] = OutboundChatBubble(
            localId,
            text.trim(),
            imagePaths,
            userLinesBefore,
            stagedAtMs = System.currentTimeMillis(),
        )
        deliveries.remove(key)
        return localId
    }

    fun rememberDelivery(
        hostId: String,
        agentId: String,
        text: String,
        attachmentLocalPaths: List<String>,
        workspace: String,
    ) {
        val key = agentKey(hostId, agentId)
        deliveries[key] = QueuedFollowUpDelivery(
            text.trim(),
            attachmentLocalPaths,
            workspace,
            pending[key]?.localId ?: UUID.randomUUID().toString(),
        )
    }

    fun beginDelivery(hostId: String, agentId: String, cid: String): Boolean =
        synchronized(inFlight) { inFlight.add("${agentKey(hostId, agentId)}:$cid") }

    fun endDelivery(hostId: String, agentId: String, cid: String) {
        synchronized(inFlight) { inFlight.remove("${agentKey(hostId, agentId)}:$cid") }
    }

    fun deliveryFor(hostId: String, agentId: String): QueuedFollowUpDelivery? =
        deliveries[agentKey(hostId, agentId)]

    fun markWaitingForLink(hostId: String, agentId: String) {
        val key = agentKey(hostId, agentId)
        val row = pending[key] ?: return
        pending[key] = row.copy(waitingForLink = true)
    }

    fun markSending(hostId: String, agentId: String) {
        val key = agentKey(hostId, agentId)
        val row = pending[key] ?: return
        pending[key] = row.copy(waitingForLink = false, daemonAcked = true)
    }

    private val userStableKeys = mutableStateMapOf<String, String>()

    fun rememberUserKey(serverMessageId: String, localId: String) {
        userStableKeys[serverMessageId] = localId
    }

    fun displayUserKey(messageId: String): String = userStableKeys[messageId] ?: messageId

    fun agentIdsWithWaitingDelivery(hostId: String): List<String> =
        pending.filter { (key, row) ->
            key.startsWith("$hostId:") && row.waitingForLink
        }.map { it.key.substringAfter(':') }

    fun clear(hostId: String, agentId: String) {
        val key = agentKey(hostId, agentId)
        pending.remove(key)
        queued.remove(key)
        deliveries.remove(key)
    }

    fun reconcile(hostId: String, agentId: String, transcriptUserLines: List<String>): OutboundChatBubble? {
        val key = agentKey(hostId, agentId)
        queued[key]?.let { earlier ->
            val left = unmatchedOutbound(earlier, transcriptUserLines)
            if (left.isEmpty()) queued.remove(key) else if (left.size != earlier.size) queued[key] = left
        }
        val row = pending[key] ?: return null
        val want = normalizeUserMatchText(row.displayText)
        val tail = transcriptUserLines.drop(row.userLinesBefore)
        val hit = tail.any { normalizeUserMatchText(it) == want }
        return if (hit) pending.remove(key) else null
    }
}

fun unmatchedOutbound(rows: List<OutboundChatBubble>, transcriptUserLines: List<String>): List<OutboundChatBubble> =
    rows.filter { row ->
        val want = normalizeUserMatchText(row.displayText)
        transcriptUserLines.drop(row.userLinesBefore).none { normalizeUserMatchText(it) == want }
    }

fun outboundIsQueued(agentWorking: Boolean, agentStatusUpdatedMs: Long, stagedAtMs: Long): Boolean =
    agentWorking && agentStatusUpdatedMs in 1 until stagedAtMs

private val imageMarker = Regex("""\[Image #\d+]""")

fun stripImageMarkers(text: String): String = imageMarker.replace(text, "").trim()

fun normalizeUserMatchText(text: String): String = stripImageMarkers(text).trim()
