package sh.simple.android.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import sh.simple.android.link.HostLinkState
import sh.simple.android.link.Hosts
import sh.simple.android.link.RemoteGateway
import sh.simple.android.link.rpcRun
import sh.simple.android.store.SimpleDatabase
import sh.simple.android.store.TokenVault
import sh.simple.android.ui.composer.uploadAttachmentLocalPaths

data class QueuedFollowUpDelivery(
    val text: String,
    val attachmentLocalPaths: List<String>,
    val workspace: String,
    val cid: String,
)

suspend fun deliverChatFollowUp(
    hosts: Hosts,
    vault: TokenVault,
    db: SimpleDatabase,
    store: ChatOutboundStore,
    hostId: String,
    agentId: String,
    delivery: QueuedFollowUpDelivery,
): Boolean? {
    if (!store.beginDelivery(hostId, agentId, delivery.cid)) return null
    try {
        val result = rpcRun {
            val paths = uploadAttachmentLocalPaths(hosts, vault, db, hostId, delivery.workspace, delivery.attachmentLocalPaths)
            val payload = RemoteGateway.promptWithAttachmentPaths(delivery.text, paths)
            RemoteGateway.send(hosts, hostId, agentId, payload, delivery.cid)
        }
        return result.isSuccess
    } finally {
        store.endDelivery(hostId, agentId, delivery.cid)
    }
}

private suspend fun deliverAndMark(
    hosts: Hosts,
    vault: TokenVault,
    db: SimpleDatabase,
    store: ChatOutboundStore,
    hostId: String,
    agentId: String,
    delivery: QueuedFollowUpDelivery,
) {
    when (deliverChatFollowUp(hosts, vault, db, store, hostId, agentId, delivery)) {
        true -> store.markSending(hostId, agentId)
        false -> store.markWaitingForLink(hostId, agentId)
        null -> Unit
    }
}

fun CoroutineScope.launchChatFollowUp(
    hosts: Hosts,
    vault: TokenVault,
    db: SimpleDatabase,
    store: ChatOutboundStore,
    hostId: String,
    agentId: String,
    text: String,
    attachmentLocalPaths: List<String>,
    workspace: String,
) {
    val delivery = store.deliveryFor(hostId, agentId)
        ?: QueuedFollowUpDelivery(text.trim(), attachmentLocalPaths, workspace, java.util.UUID.randomUUID().toString())
    launch { deliverAndMark(hosts, vault, db, store, hostId, agentId, delivery) }
}

suspend fun retryWaitingFollowUps(
    hosts: Hosts,
    vault: TokenVault,
    db: SimpleDatabase,
    store: ChatOutboundStore,
    hostId: String,
) {
    for (agentId in store.agentIdsWithWaitingDelivery(hostId)) {
        val delivery = store.deliveryFor(hostId, agentId) ?: continue
        deliverAndMark(hosts, vault, db, store, hostId, agentId, delivery)
    }
}

@Composable
fun ChatFollowUpAutoRetryEffect(
    hosts: Hosts,
    hostId: String,
    store: ChatOutboundStore,
    vault: TokenVault,
    db: SimpleDatabase,
) {
    LaunchedEffect(hostId) {
        val link = hosts.link(hostId) ?: return@LaunchedEffect
        var wasLive = false
        link.state.collect { next ->
            val live = next is HostLinkState.Live
            if (live && !wasLive) {
                retryWaitingFollowUps(hosts, vault, db, store, hostId)
            }
            wasLive = live
        }
    }
}
