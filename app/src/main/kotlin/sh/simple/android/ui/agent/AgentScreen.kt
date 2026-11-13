package sh.simple.android.ui.agent

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.kyant.shapes.Capsule
import java.util.Base64
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import sh.simple.android.link.Hosts
import sh.simple.android.link.HostLinkState
import sh.simple.android.link.PanesGateway
import sh.simple.android.link.launchRpc
import sh.simple.android.link.rpcErrorMessage
import sh.simple.android.model.Pane
import sh.simple.android.model.TermOpenResult
import sh.simple.android.model.WireJson
import sh.simple.android.store.CodeFontStore
import sh.simple.android.store.SimpleDatabase
import sh.simple.android.store.TerminalScreenCache
import sh.simple.android.ui.glass.GlassStage
import sh.simple.android.ui.glass.rememberPress
import sh.simple.android.ui.kit.AppText
import sh.simple.android.ui.kit.LoadingRow
import sh.simple.android.ui.pane.PaneSwitcherRow
import sh.simple.android.ui.pane.paneSwipeNeighbors
import sh.simple.android.ui.kit.HeaderBar
import sh.simple.android.ui.kit.Icons
import sh.simple.android.ui.kit.MinTouchTarget
import sh.simple.android.ui.kit.focusRing
import sh.simple.android.store.TerminalKeyBarStore
import sh.simple.android.store.TerminalThemeStore
import sh.simple.android.ui.terminal.RemoteTerminalView
import sh.simple.android.ui.terminal.TerminalThemes
import sh.simple.android.ui.terminal.terminalTypeface
import sh.simple.android.ui.terminal.toComposeBackground
import sh.simple.android.ui.theme.LocalAppearance
import sh.simple.android.ui.theme.Tokens
import sh.simple.android.ui.theme.Type

private enum class TermPhase { Connecting, Live, Ended }

@Composable
fun AgentScreen(
    hosts: Hosts,
    db: SimpleDatabase,
    hostId: String,
    agentId: String,
    title: String,
    terminalTheme: TerminalThemeStore,
    terminalKeys: TerminalKeyBarStore,
    codeFont: CodeFontStore,
    onBack: () -> Unit,
    onSwapAgent: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val appearance = LocalAppearance.current
    val systemDark = isSystemInDarkTheme()
    val appDark = appearance.resolvesDark(systemDark)
    val palette = TerminalThemes.resolve(terminalTheme.themeId, appDark)
    val terminalBackground = palette.toComposeBackground()
    val agents by db.agents().observeAll().collectAsState(initial = emptyList())
    val currentAgent = agents.firstOrNull { it.hostId == hostId && it.id == agentId }
    var workspacePanes by remember(hostId, agentId) { mutableStateOf<List<Pane>>(emptyList()) }
    LaunchedEffect(hostId, agentId, currentAgent?.workspace, currentAgent?.cwd) {
        val ws = currentAgent?.workspace?.takeIf { it.isNotEmpty() } ?: currentAgent?.cwd.orEmpty()
        if (ws.isEmpty()) {
            workspacePanes = emptyList()
            return@LaunchedEffect
        }
        launchRpc {
            workspacePanes = runCatching {
                PanesGateway.list(hosts, hostId, ws).tabs.flatMap { it.panes }
            }.getOrDefault(emptyList())
        }
    }
    val usePaneSwipe = workspacePanes.size >= 2
    val swipeState = rememberAgentSwipeState()
    val (_, panePrev, paneNext) = remember(workspacePanes, agentId) {
        paneSwipeNeighbors(workspacePanes, agentId)
    }
    val (_, agentPrev, agentNext) = agentSwipeNeighbors(agents, hostId, agentId)
    val swipePrev = if (usePaneSwipe) panePrev?.let { AgentSwipeNeighbor(hostId, it.id) } else agentPrev
    val swipeNext = if (usePaneSwipe) paneNext?.let { AgentSwipeNeighbor(hostId, it.id) } else agentNext
    val swipeEnabled = swipePrev != null || swipeNext != null
    val swipeCommit: (AgentSwipeNeighbor) -> Unit = { n -> onSwapAgent(n.hostId, n.agentId) }
    LaunchedEffect(hostId, agentId) { swipeState.resetDrag() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val view = remember { RemoteTerminalView(context) }
    LaunchedEffect(palette) {
        view.applyPalette(palette)
    }
    LaunchedEffect(codeFont.choice, codeFont.localFile.path) {
        view.setTerminalTypeface(terminalTypeface(context, codeFont))
    }
    var grid by remember { mutableStateOf(0 to 0) }
    var sid by remember { mutableStateOf<String?>(null) }
    var phase by remember { mutableStateOf(TermPhase.Connecting) }
    var ctrl by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    var termError by remember { mutableStateOf("") }
    var hasScreen by remember(hostId, agentId) {
        mutableStateOf(TerminalScreenCache.get(hostId, agentId) != null)
    }
    var showReconnecting by remember { mutableStateOf(false) }
    var lastFrameAtMs by remember { mutableStateOf(0L) }
    var linkLive by remember { mutableStateOf(true) }

    LaunchedEffect(hostId, agentId) {
        TerminalScreenCache.get(hostId, agentId)?.let { cached ->
            view.feed(cached, full = true)
            hasScreen = true
            phase = TermPhase.Live
        }
    }

    LaunchedEffect(hostId) {
        val link = hosts.link(hostId) ?: return@LaunchedEffect
        var wasLive = link.state.value is HostLinkState.Live
        link.state.collect { state ->
            val live = state is HostLinkState.Live
            linkLive = live
            if (live && !wasLive) {
                sid = null
                attempt++
            }
            wasLive = live
        }
    }

    view.onGridSize = { cols, rows -> grid = cols to rows }
    view.ctrlArmed = { ctrl }
    view.consumeCtrl = { ctrl = false }
    view.onInput = { bytes -> sid?.let { s -> hosts.link(hostId)?.sendTermInput(s, bytes) } }

    LaunchedEffect(phase) {
        if (phase == TermPhase.Ended) {
            delay(400)
            attempt++
        }
    }

    LaunchedEffect(lastFrameAtMs, phase, sid, linkLive) {
        showReconnecting = false
        if (!terminalNeedsReconnectHint(linkLive, phase == TermPhase.Live && sid != null, lastFrameAtMs)) {
            return@LaunchedEffect
        }
        delay(2_000)
        showReconnecting = true
    }

    LaunchedEffect(hostId, agentId, attempt, grid.first > 0) {
        val (cols, rows) = grid
        if (cols == 0) return@LaunchedEffect
        val link = hosts.link(hostId) ?: return@LaunchedEffect
        link.state.first { it is HostLinkState.Live }
        if (!hasScreen) phase = TermPhase.Connecting
        termError = ""
        lastFrameAtMs = 0L
        val openResult = runCatching {
            val r = hosts.rpc(
                hostId,
                "term.open",
                buildJsonObject {
                    put("id", agentId)
                    put("cols", cols)
                    put("rows", rows)
                },
            )
            WireJson.decodeFromJsonElement(TermOpenResult.serializer(), r).sid
        }
        val opened = openResult.getOrNull()
        if (opened == null) {
            termError = openResult.exceptionOrNull()?.let { rpcErrorMessage(it) }.orEmpty()
            phase = TermPhase.Ended
            return@LaunchedEffect
        }
        sid = opened
        phase = TermPhase.Live
        var gotFrame = false
        val snapJob = launch {
            delay(1_500)
            if (!gotFrame) {
                runCatching {
                    hosts.rpc(hostId, "term.snap", buildJsonObject { put("sid", opened) })
                }
            }
        }
        for (frame in link.termFrames) {
            if (frame.t != opened) continue
            frame.b?.let { encoded ->
                val decoded = Base64.getDecoder().decode(encoded)
                view.feed(decoded, frame.full)
                TerminalScreenCache.record(hostId, agentId, decoded, frame.full)
                hasScreen = true
                gotFrame = true
                lastFrameAtMs = System.currentTimeMillis()
                showReconnecting = false
            }
            if (frame.done) {
                phase = TermPhase.Ended
                snapJob.cancel()
                break
            }
        }
        snapJob.cancel()
    }
    LaunchedEffect(grid) {
        val s = sid ?: return@LaunchedEffect
        val (cols, rows) = grid
        runCatching {
            hosts.rpc(
                hostId,
                "term.resize",
                buildJsonObject {
                    put("sid", s)
                    put("cols", cols)
                    put("rows", rows)
                },
            )
        }
    }
    DisposableEffect(hostId, agentId) {
        onDispose {
            val s = sid ?: return@onDispose
            scope.launchRpc { hosts.rpc(hostId, "term.close", buildJsonObject { put("sid", s) }) }
        }
    }

    GlassStage(
        modifier.fillMaxSize().background(Tokens.current.background),
        chrome = {
            if (swipeEnabled) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 52.dp)
                        .height(agentSwipeChromeHeight())
                        .agentSwipeGesture(
                            enabled = true,
                            state = swipeState,
                            hasPrev = swipePrev != null,
                            hasNext = swipeNext != null,
                            onCommit = swipeCommit,
                            prev = swipePrev,
                            next = swipeNext,
                        ),
                )
            }
        },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .imePadding()
                .onGloballyPositioned { swipeState.onWidthMeasured(it.size.width.toFloat()) },
        ) {
            Column(Modifier.fillMaxSize().agentSwipeMotion(swipeState)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .agentSwipeGesture(
                            enabled = swipeEnabled,
                            state = swipeState,
                            hasPrev = swipePrev != null,
                            hasNext = swipeNext != null,
                            onCommit = swipeCommit,
                            prev = swipePrev,
                            next = swipeNext,
                        ),
                ) {
                    HeaderBar(
                        listOf(Icons.Back),
                        agents.firstOrNull { it.hostId == hostId && it.id == agentId }?.title?.ifBlank { null } ?: title,
                        emptyList(),
                        onLeftClick = { _ -> onBack() },
                    )
                    if (swipeEnabled) AgentSwipeHintLine()
                }
                PaneSwitcherRow(
                    panes = workspacePanes,
                    currentId = agentId,
                    onPick = { id -> onSwapAgent(hostId, id) },
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(terminalBackground)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                ) {
                    AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
                    if (phase == TermPhase.Connecting && !hasScreen) {
                        LoadingRow(
                            lines = 2,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                    if (showReconnecting) {
                        AppText(
                            "Reconnecting",
                            style = Type.meta,
                            color = Tokens.current.muted,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 8.dp),
                        )
                    }
                    if (termError.isNotEmpty() && phase == TermPhase.Ended && !hasScreen) {
                        AppText(
                            termError,
                            style = Type.meta,
                            color = Tokens.current.muted,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
                KeyBar(
                    ctrl = ctrl,
                    customKeys = terminalKeys.entries,
                    onCtrl = { ctrl = !ctrl },
                    onKey = { code -> view.sendKey(code) },
                    onText = { text -> view.sendText(text) },
                    onSpec = { spec -> view.sendBarSpec(spec) },
                    onKeyboard = { view.showKeyboard() },
                    onPaste = { view.paste() },
                    onCopy = { view.copyScreen() },
                )
            }
        }
    }
}

@Composable
private fun KeyBar(
    ctrl: Boolean,
    customKeys: List<sh.simple.android.store.TerminalKeyEntry>,
    onCtrl: () -> Unit,
    onKey: (Int) -> Unit,
    onText: (String) -> Unit,
    onSpec: (String) -> Unit,
    onKeyboard: () -> Unit,
    onPaste: () -> Unit,
    onCopy: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BarKey("esc") { onText("\u001b") }
        BarKey("tab") { onText("\t") }
        BarKey("ctrl", active = ctrl, toggle = true, onClick = onCtrl)
        BarKey("↑") { onKey(KeyEvent.KEYCODE_DPAD_UP) }
        BarKey("↓") { onKey(KeyEvent.KEYCODE_DPAD_DOWN) }
        BarKey("←") { onKey(KeyEvent.KEYCODE_DPAD_LEFT) }
        BarKey("→") { onKey(KeyEvent.KEYCODE_DPAD_RIGHT) }
        BarKey("enter") { onText("\r") }
        BarKey("/") { onText("/") }
        BarKey("|") { onText("|") }
        BarKey("~") { onText("~") }
        BarKey("ctrl c") { onText("\u0003") }
        for (entry in customKeys) {
            BarKey(entry.label) { onSpec(entry.spec) }
        }
        BarKey("paste", onClick = onPaste)
        BarKey("copy screen", onClick = onCopy)
        BarKey("keyboard", onClick = onKeyboard)
    }
}

@Composable
private fun BarKey(label: String, active: Boolean = false, toggle: Boolean = false, onClick: () -> Unit) {
    val (source, pressed) = rememberPress()
    val shape = Capsule()
    Box(
        Modifier
            .heightIn(min = MinTouchTarget)
            .graphicsLayer { alpha = if (pressed) 0.55f else 1f }
            .then(
                if (toggle) {
                    Modifier.semantics {
                        if (active) stateDescription = "On" else stateDescription = "Off"
                    }
                } else {
                    Modifier
                },
            )
            .background(if (active) Tokens.current.primaryFill else Tokens.current.raised, shape)
            .border(1.dp, Tokens.current.line, shape)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
            .focusRing(shape)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        AppText(
            label,
            style = Type.chip,
            color = if (active) Tokens.current.onPrimary else Tokens.current.foreground,
        )
    }
}

internal fun terminalNeedsReconnectHint(linkLive: Boolean, attached: Boolean, lastFrameAtMs: Long): Boolean =
    !linkLive || (attached && lastFrameAtMs == 0L)
