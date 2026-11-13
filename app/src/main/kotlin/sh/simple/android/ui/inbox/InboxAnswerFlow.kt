package sh.simple.android.ui.inbox

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.snapshots.SnapshotStateSet
import sh.simple.android.link.HostLinkState
import sh.simple.android.link.Hosts
import sh.simple.android.link.RemoteGateway
import sh.simple.android.link.rpcErrorMessage
import sh.simple.android.link.rpcRun

data class InboxAnswerRequest(
    val decision: String,
    val option: String?,
    val text: String?,
)

internal data class PendingInboxAnswer(
    val hostId: String,
    val itemId: String,
    val request: InboxAnswerRequest,
)

class InboxAnswerUiState internal constructor(
    val sent: SnapshotStateMap<String, Boolean>,
    val errors: SnapshotStateMap<String, String>,
    private val pending: SnapshotStateMap<String, PendingInboxAnswer>,
    private val autoRetried: SnapshotStateSet<String>,
) {
    fun pendingKey(hostId: String, itemId: String) = "$hostId:$itemId"

    suspend fun submit(
        hosts: Hosts,
        hostId: String,
        itemId: String,
        request: InboxAnswerRequest,
        onSuccess: suspend () -> Unit = {},
        registerRetry: Boolean = true,
    ): Boolean {
        errors.remove(itemId)
        val result = rpcRun {
            RemoteGateway.inboxAnswer(hosts, hostId, itemId, request.decision, request.option, request.text)
        }
        if (result.isSuccess) {
            sent[itemId] = true
            pending.remove(pendingKey(hostId, itemId))
            autoRetried.remove(pendingKey(hostId, itemId))
            onSuccess()
            return true
        }
        errors[itemId] = rpcErrorMessage(result.exceptionOrNull()!!)
        if (registerRetry) {
            pending[pendingKey(hostId, itemId)] = PendingInboxAnswer(hostId, itemId, request)
        } else {
            pending.remove(pendingKey(hostId, itemId))
        }
        return false
    }

    suspend fun retryPendingForHost(hosts: Hosts, hostId: String, onSuccess: suspend (itemId: String) -> Unit) {
        val keys = pending.keys.filter { it.startsWith("$hostId:") }.toList()
        for (key in keys) {
            if (autoRetried.contains(key)) continue
            val row = pending[key] ?: continue
            autoRetried.add(key)
            if (submit(hosts, row.hostId, row.itemId, row.request, { onSuccess(row.itemId) }, registerRetry = false)) {
                continue
            }
        }
    }
}

@Composable
fun rememberInboxAnswerUiState(): InboxAnswerUiState {
    val sent = remember { mutableStateMapOf<String, Boolean>() }
    val errors = remember { mutableStateMapOf<String, String>() }
    val pending = remember { mutableStateMapOf<String, PendingInboxAnswer>() }
    val autoRetried = remember { mutableStateSetOf<String>() }
    return remember { InboxAnswerUiState(sent, errors, pending, autoRetried) }
}

@Composable
fun InboxAnswerAutoRetryEffect(
    hosts: Hosts,
    hostId: String,
    state: InboxAnswerUiState,
    onDelivered: suspend (itemId: String) -> Unit,
) {
    LaunchedEffect(hostId) {
        val link = hosts.link(hostId) ?: return@LaunchedEffect
        var wasLive = link.state.value is HostLinkState.Live
        link.state.collect { next ->
            val live = next is HostLinkState.Live
            if (live && !wasLive) {
                state.retryPendingForHost(hosts, hostId, onDelivered)
            }
            wasLive = live
        }
    }
}

@Composable
fun InboxAnswerAutoRetryAllHostsEffect(
    hosts: Hosts,
    hostIds: Set<String>,
    state: InboxAnswerUiState,
    onDelivered: suspend (hostId: String, itemId: String) -> Unit,
) {
    hostIds.forEach { hostId ->
        InboxAnswerAutoRetryEffect(hosts, hostId, state) { itemId ->
            onDelivered(hostId, itemId)
        }
    }
}
