package sh.simple.android.link

import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import sh.simple.android.model.AgentsListResult
import sh.simple.android.model.WorkspacesListResult
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import sh.simple.android.model.HelloParams
import sh.simple.android.model.HelloRequest
import sh.simple.android.model.HelloResult
import sh.simple.android.model.WireJson
import sh.simple.android.model.TermFrame
import sh.simple.android.model.WsError
import sh.simple.android.model.WsEvent
import sh.simple.android.model.WsRequest
import sh.simple.android.model.WsResult
import sh.simple.android.store.HostRecord
import sh.simple.android.store.Sync
import sh.simple.android.store.TokenVault

sealed interface HostLinkState {
    data object Connecting : HostLinkState
    data object Live : HostLinkState
    data class Offline(val retryAtMs: Long?) : HostLinkState
    data object Revoked : HostLinkState
}

class LinkDroppedException(
    val kind: String = "disconnected",
    detail: String? = null,
) : Exception(detail ?: kind)

class HostLink(
    private val record: HostRecord,
    private val vault: TokenVault,
    private val sync: Sync,
    private val network: NetworkMonitor,
    private val scope: CoroutineScope,
    private val appVersion: String = "0.1.0",
) {
    private val _state = MutableStateFlow<HostLinkState>(HostLinkState.Connecting)
    val state: StateFlow<HostLinkState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<WsEvent>(extraBufferCapacity = 128)
    val events: SharedFlow<WsEvent> = _events.asSharedFlow()

    private val _termFrames = Channel<TermFrame>(Channel.UNLIMITED)
    val termFrames: ReceiveChannel<TermFrame> = _termFrames

    fun sendTermInput(sid: String, bytes: ByteArray): Boolean {
        val frame = WireJson.encodeToString(TermFrame(sid, Base64.getEncoder().encodeToString(bytes)))
        return socket?.send(frame) == true
    }

    private val backoff = ReconnectBackoff()
    private val callMutex = Mutex()
    private val pending = ConcurrentHashMap<Long, CompletableDeferred<JsonElement>>()
    private val nextId = AtomicLong(1)
    @Volatile private var socket: WebSocket? = null
    private var closed = CompletableDeferred<Unit>()
    private var loopJob: Job? = null
    private var networkJob: Job? = null
    @Volatile private var helloWait: CompletableDeferred<String>? = null
    @Volatile private var revoked = false

    fun start() {
        if (loopJob != null) return
        networkJob = scope.launch {
            network.online().collect { up ->
                if (up) {
                    backoff.reset()
                    socket?.close(1000, "network")
                }
            }
        }
        loopJob = scope.launch { runLoop() }
    }

    suspend fun stop() {
        networkJob?.cancelAndJoin()
        loopJob?.cancelAndJoin()
        socket?.close(1000, "bye")
        socket = null
        failPending(LinkDroppedException("stopped"))
    }

    suspend fun call(method: String, params: JsonElement? = null): JsonElement {
        val id = nextId.getAndIncrement()
        val deferred = CompletableDeferred<JsonElement>()
        pending[id] = deferred
        val frame = WireJson.encodeToString(WsRequest(id, method, params))
        val sent = callMutex.withLock { socket?.send(frame) == true }
        if (!sent) {
            pending.remove(id)
            throw LinkDroppedException("send failed")
        }
        try {
            return withTimeoutOrNull(60_000) { deferred.await() } ?: throw LinkDroppedException("timeout")
        } finally {
            pending.remove(id)
        }
    }

    private suspend fun runLoop() {
        if (record.tokenCipher.isEmpty()) {
            _state.value = HostLinkState.Revoked
            return
        }
        while (scope.isActive) {
            _state.value = HostLinkState.Connecting
            closed = CompletableDeferred()
            try {
                openSocket()
                performHello()
                backoff.reset()
                _state.value = HostLinkState.Live
                closed.await()
            } catch (_: Exception) {
            } finally {
                socket?.close(1000, "bye")
                socket = null
                failPending(LinkDroppedException())
            }
            if (revoked) {
                _state.value = HostLinkState.Revoked
                return
            }
            val delayMs = backoff.nextDelayMs()
            _state.value = HostLinkState.Offline(System.currentTimeMillis() + delayMs)
            delay(delayMs)
        }
    }

    private suspend fun openSocket() {
        val token = vault.decrypt(record.tokenCipher)
        var last: Exception? = null
        for (addr in record.addresses) {
            val base = if (addr.contains("://")) addr else "https://$addr"
            val wsUrl = base.trimEnd('/').replaceFirst("https://", "wss://").replaceFirst("http://", "ws://") + "/v1/ws"
            val client = PinnedTls.client(record.fingerprint).newBuilder()
                .pingInterval(15, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build()
            val request = Request.Builder()
                .url(wsUrl)
                .header("Authorization", "Bearer $token")
                .build()
            val opened = CompletableDeferred<Unit>()
            val ws = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    socket = webSocket
                    opened.complete(Unit)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    routeMessage(text)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (!closed.isCompleted) closed.complete(Unit)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (response?.code == 401) revoked = true
                    opened.completeExceptionally(t)
                    if (!closed.isCompleted) closed.complete(Unit)
                }
            })
            try {
                withTimeout(15_000) { opened.await() }
                return
            } catch (e: Exception) {
                ws.cancel()
                last = e
                if (revoked) break
            }
        }
        throw last ?: LinkDroppedException("unreachable")
    }

    private suspend fun performHello() {
        val wait = CompletableDeferred<String>()
        helloWait = wait
        val params = HelloParams(
            app = appVersion,
            epoch = record.epoch,
            since = record.epoch?.let { record.seq },
        )
        val hello = WireJson.encodeToString(HelloRequest("hello", params))
        if (socket?.send(hello) != true) throw LinkDroppedException("hello send")
        val raw = withTimeout(30_000) { wait.await() }
        helloWait = null
        val resultEl = WireJson.parseToJsonElement(raw).jsonObject["r"]
            ?: throw LinkDroppedException("hello response")
        val helloResult = WireJson.decodeFromJsonElement<HelloResult>(resultEl)
        sync.applyHello(record.id, helloResult)
        HostRuntimes.set(helloResult.host.id.ifBlank { record.id }, helloResult.host.hc, helloResult.host.herdr)
        hydrateCatalogIfEmpty(helloResult.host.id.ifBlank { record.id })
    }

    private suspend fun hydrateCatalogIfEmpty(hostId: String) {
        if (sync.countAgents(hostId) > 0) return
        val agentsRaw = call("agents.list", buildJsonObject {})
        val agents = WireJson.decodeFromJsonElement<AgentsListResult>(agentsRaw).agents
        val workspacesRaw = call("workspaces.list", buildJsonObject {})
        val workspaces = WireJson.decodeFromJsonElement<WorkspacesListResult>(workspacesRaw).workspaces
        sync.applyCatalog(hostId, agents, workspaces)
    }

    private fun routeMessage(text: String) {
        helloWait?.let { wait ->
            if (!wait.isCompleted) {
                wait.complete(text)
                return
            }
        }
        val el = runCatching { WireJson.parseToJsonElement(text) }.getOrNull() ?: return
        val obj = el.jsonObject
        when {
            "t" in obj -> _termFrames.trySend(WireJson.decodeFromJsonElement<TermFrame>(el))
            "ev" in obj -> {
                val event = WireJson.decodeFromJsonElement<WsEvent>(el)
                scope.launch {
                    sync.applyEvent(record.id, event)
                    _events.emit(event)
                }
            }
            "e" in obj && "id" in obj -> {
                val err = WireJson.decodeFromJsonElement<WsError>(el)
                pending.remove(err.id)?.completeExceptionally(
                    LinkDroppedException(
                        err.e.kind,
                        err.e.msg?.takeIf { it.isNotBlank() },
                    ),
                )
            }
            "r" in obj && "id" in obj -> {
                val res = WireJson.decodeFromJsonElement<WsResult>(el)
                pending.remove(res.id)?.complete(res.r)
            }
        }
    }

    private fun failPending(cause: Exception) {
        pending.values.forEach { it.completeExceptionally(cause) }
        pending.clear()
    }
}
