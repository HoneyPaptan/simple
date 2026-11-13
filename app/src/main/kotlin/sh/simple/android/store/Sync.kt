package sh.simple.android.store

import sh.simple.android.link.HostRuntimes
import androidx.room.withTransaction
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.decodeFromJsonElement
import sh.simple.android.model.Agent
import sh.simple.android.model.AgentRemoved
import sh.simple.android.model.HelloResult
import sh.simple.android.model.Item
import sh.simple.android.model.Message
import sh.simple.android.model.Snapshot
import sh.simple.android.model.WireJson
import sh.simple.android.model.Workspace
import sh.simple.android.model.WsEvent

class Sync(
    private val db: SimpleDatabase,
    private val onInboxAdd: (String, Item) -> Unit = { _, _ -> },
    private val onInboxUpdate: (String, Item) -> Unit = { _, _ -> },
) {
    private val hosts = db.hosts()
    private val agents = db.agents()
    private val items = db.items()
    private val workspaces = db.workspaces()
    private val messages = db.messages()

    suspend fun countAgents(hostId: String): Int = agents.countForHost(hostId)

    suspend fun upsertAgent(hostId: String, agent: Agent) {
        agents.upsertChanged(listOf(agent.toEntity(hostId)))
    }

    suspend fun applyHello(linkHostId: String, hello: HelloResult) {
        val hostId = hello.host.id.ifBlank { linkHostId }
        val changes = db.withTransaction {
            hosts.updateCursor(hostId, hello.host.epoch, hello.seq)
            val snap = hello.snapshot ?: return@withTransaction InboxChanges.NONE
            replaceSnapshot(hostId, snap)
        }
        announce(hostId, changes)
    }

    suspend fun applyCatalog(
        hostId: String,
        agentList: List<Agent>,
        workspaceList: List<Workspace>,
        inboxList: List<Item> = emptyList(),
    ) {
        val changes = db.withTransaction {
            replaceSnapshot(
                hostId,
                Snapshot(agents = agentList, inbox = inboxList, workspaces = workspaceList),
            )
        }
        announce(hostId, changes)
    }

    private fun announce(hostId: String, changes: InboxChanges) {
        changes.added.forEach { onInboxAdd(hostId, it) }
        changes.gone.forEach { onInboxUpdate(hostId, it) }
    }

    suspend fun applyEvent(hostId: String, event: WsEvent) {
        db.withTransaction {
            when (event.ev) {
                "agent.update" -> {
                    val agent = WireJson.decodeFromJsonElement<Agent>(event.d)
                    agents.upsertChanged(listOf(agent.toEntity(hostId)))
                }
                "agent.removed" -> {
                    val removed = WireJson.decodeFromJsonElement<AgentRemoved>(event.d)
                    agents.delete(hostId, removed.id)
                }
                "workspace.update" -> {
                    val ws = WireJson.decodeFromJsonElement<Workspace>(event.d)
                    if (ws.agents <= 0) {
                        workspaces.delete(hostId, ws.path)
                    } else {
                        workspaces.upsertChanged(listOf(ws.toEntity(hostId)))
                    }
                }
                "host.update" -> HostRuntimes.applyUpdate(hostId, event.d)
                "inbox.add", "inbox.update" -> {
                    val item = WireJson.decodeFromJsonElement<Item>(event.d)
                    items.upsertChanged(listOf(item.toEntity(hostId)))
                    when (event.ev) {
                        "inbox.add" -> onInboxAdd(hostId, item)
                        "inbox.update" -> onInboxUpdate(hostId, item)
                    }
                }
                "transcript.append" -> {
                    val payload = WireJson.decodeFromJsonElement<TranscriptAppend>(event.d)
                    messages.upsertChanged(
                        payload.messages.map { msg ->
                            MessageEntity(
                                hostId = hostId,
                                agentId = payload.id,
                                id = msg.id,
                                role = msg.role,
                                ts = msg.ts,
                                json = WireJson.encodeToString(msg),
                            )
                        },
                    )
                }
            }
            hosts.updateCursor(hostId, null, event.seq)
        }
    }

    private suspend fun replaceSnapshot(hostId: String, snap: Snapshot): InboxChanges {
        val before = items.listAll()
            .filter { it.hostId == hostId }
            .mapNotNull { runCatching { WireJson.decodeFromString<Item>(it.json) }.getOrNull() }
        val changes = inboxChanges(before, snap.inbox)
        agents.deleteForHost(hostId)
        items.deleteForHost(hostId)
        workspaces.deleteForHost(hostId)
        agents.upsertChanged(snap.agents.map { it.toEntity(hostId) })
        messages.deleteForHostExcept(hostId, snap.agents.map { it.id })
        items.upsertChanged(snap.inbox.map { it.toEntity(hostId) })
        workspaces.upsertChanged(snap.workspaces.map { it.toEntity(hostId) })
        return changes
    }
}

internal data class InboxChanges(val added: List<Item>, val gone: List<Item>) {
    companion object {
        val NONE = InboxChanges(emptyList(), emptyList())
    }
}

internal fun inboxChanges(before: List<Item>, after: List<Item>): InboxChanges {
    val openBefore = before.filter { !it.resolved }.associateBy { it.id }
    val openAfter = after.filter { !it.resolved }.associateBy { it.id }
    val added = openAfter.values.filter { it.id !in openBefore }
    val gone = openBefore.values.filter { it.id !in openAfter }.map { it.copy(resolved = true) }
    return InboxChanges(added, gone)
}

@kotlinx.serialization.Serializable
private data class TranscriptAppend(
    val id: String,
    val messages: List<Message> = emptyList(),
)

private fun Agent.toEntity(hostId: String) = AgentEntity(
    hostId = hostId,
    id = id,
    kind = kind,
    title = title,
    cwd = cwd,
    workspace = workspace,
    status = status,
    waiting = waiting,
    model = model,
    mode = mode,
    updated = updated_ms,
    pinned = pinned,
    unread = unread,
    json = WireJson.encodeToString(this),
)

private fun Item.toEntity(hostId: String) = ItemEntity(
    hostId = hostId,
    id = id,
    json = WireJson.encodeToString(this),
)

private fun Workspace.toEntity(hostId: String) = WorkspaceEntity(
    hostId = hostId,
    path = path,
    name = name,
    branch = branch,
    dirty = dirty,
    agents = agents,
    pinned = pinned,
    updated = updated_ms,
    json = WireJson.encodeToString(this),
)
