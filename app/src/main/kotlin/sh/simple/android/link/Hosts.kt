package sh.simple.android.link

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import sh.simple.android.model.WsEvent
import sh.simple.android.store.SimpleDatabase
import sh.simple.android.store.Sync
import sh.simple.android.store.TokenVault
import sh.simple.android.store.toRecord

data class HostConnectionRow(
    val name: String,
    val state: HostLinkState,
)

class Hosts(
    private val db: SimpleDatabase,
    private val vault: TokenVault,
    network: NetworkMonitor,
    private val scope: kotlinx.coroutines.CoroutineScope,
    onInboxAdd: (String, sh.simple.android.model.Item) -> Unit = { _, _ -> },
    onInboxUpdate: (String, sh.simple.android.model.Item) -> Unit = { _, _ -> },
) {
    private val sync = Sync(db, onInboxAdd, onInboxUpdate)
    private val network = network
    private val links = mutableMapOf<String, HostLink>()
    private val mutex = Mutex()

    suspend fun start() {
        mutex.withLock {
            val rows = db.hosts().listAll()
            val ids = rows.map { it.id }.toSet()
            for (id in links.keys.filter { it !in ids }.toList()) {
                links.remove(id)?.stop()
            }
            for (row in rows) {
                if (links.containsKey(row.id)) continue
                val link = HostLink(row.toRecord(), vault, sync, network, scope)
                link.start()
                links[row.id] = link
            }
        }
    }

    suspend fun stop() {
        mutex.withLock {
            for (link in links.values) {
                link.stop()
            }
            links.clear()
        }
    }

    suspend fun refreshAfterPair() {
        mutex.withLock {
            for (link in links.values) {
                link.stop()
            }
            links.clear()
        }
        start()
    }

    suspend fun connectionSnapshot(): List<HostConnectionRow> = mutex.withLock {
        db.hosts().listAll().map { row ->
            val state = links[row.id]?.state?.value ?: HostLinkState.Offline(null)
            HostConnectionRow(row.name, state)
        }
    }

    fun watchConnectionRows(scope: kotlinx.coroutines.CoroutineScope, onRows: suspend (List<HostConnectionRow>) -> Unit) {
        scope.launch {
            onRows(connectionSnapshot())
            val linkList = mutex.withLock { links.values.toList() }
            for (link in linkList) {
                launch {
                    link.state.collect {
                        onRows(connectionSnapshot())
                    }
                }
            }
        }
    }

    fun events(hostId: String): SharedFlow<WsEvent>? = links[hostId]?.events

    fun link(hostId: String): HostLink? = links[hostId]

    suspend fun rpc(hostId: String, method: String, params: JsonElement? = null): JsonElement {
        val link = mutex.withLock { links[hostId] } ?: throw LinkDroppedException("offline")
        return link.call(method, params)
    }

    suspend fun removeHost(hostId: String) {
        mutex.withLock {
            links.remove(hostId)?.stop()
        }
        db.hosts().delete(hostId)
        db.agents().deleteForHost(hostId)
        db.items().deleteForHost(hostId)
        db.workspaces().deleteForHost(hostId)
        db.messages().deleteForHost(hostId)
        db.messageImages().deleteForHost(hostId)
    }
}
