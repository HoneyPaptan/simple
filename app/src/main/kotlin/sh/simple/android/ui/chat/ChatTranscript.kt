package sh.simple.android.ui.chat

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sh.simple.android.link.Hosts
import sh.simple.android.link.RemoteGateway
import sh.simple.android.model.Message
import sh.simple.android.model.WireJson
import sh.simple.android.store.MessageEntity
import sh.simple.android.store.SimpleDatabase
import sh.simple.android.store.upsertChanged

fun decodeStoredMessages(rows: List<MessageEntity>): List<Message> =
    rows.mapNotNull { row ->
        runCatching { WireJson.decodeFromString<Message>(row.json) }.getOrNull()
    }

suspend fun decodeStoredMessagesAsync(rows: List<MessageEntity>): List<Message> =
    withContext(Dispatchers.Default) { decodeStoredMessages(rows) }

suspend fun syncAgentTranscript(hosts: Hosts, db: SimpleDatabase, hostId: String, agentId: String) {
    var before: String? = null
    val pageLimit = 80
    val cached = db.messages().idsForAgent(hostId, agentId).toHashSet()
    val fetched = HashSet<String>()
    var since = 0L
    while (true) {
        val page = runCatching {
            RemoteGateway.transcript(hosts, hostId, agentId, before, pageLimit)
        }.getOrNull() ?: break
        if (before == null) since = page.since
        if (page.messages.isEmpty()) break
        val reachedCache = page.messages.minBy { it.ts }.id in cached
        page.messages.mapTo(fetched) { it.id }
        db.messages().upsertChanged(
            page.messages.map { msg ->
                MessageEntity(
                    hostId = hostId,
                    agentId = agentId,
                    id = msg.id,
                    role = msg.role,
                    ts = msg.ts,
                    json = WireJson.encodeToString(msg),
                )
            },
        )
        if (reachedCache) break
        before = page.before ?: break
    }
    if (since <= 0L || fetched.isEmpty()) return
    staleCachedIds(db.messages().idsBefore(hostId, agentId, since), fetched)
        .chunked(500)
        .forEach { db.messages().deleteIds(hostId, agentId, it) }
}

fun staleCachedIds(olderThanChat: List<String>, fetched: Set<String>): List<String> =
    olderThanChat.filter { it !in fetched }
