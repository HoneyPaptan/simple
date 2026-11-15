package sh.simple.android.ui.chat

import androidx.compose.runtime.withFrameNanos
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.composed
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.simple.android.App
import sh.simple.android.link.HostLinkState
import sh.simple.android.link.Hosts
import sh.simple.android.link.RemoteGateway
import sh.simple.android.link.launchRpc
import sh.simple.android.model.Agent
import sh.simple.android.model.Item
import sh.simple.android.model.Message
import sh.simple.android.model.ScreenPayload
import sh.simple.android.model.WireJson
import sh.simple.android.store.Commands
import sh.simple.android.store.Models
import sh.simple.android.store.SimpleDatabase
import sh.simple.android.store.rememberAgents
import sh.simple.android.store.rememberItems
import sh.simple.android.store.rememberMessages
import sh.simple.android.store.modelDisplayLabel
import sh.simple.android.ui.model.ModelPickerSheets
import sh.simple.android.ui.model.PermissionModeMenu
import sh.simple.android.ui.model.modeMenuLabel
import sh.simple.android.ui.glass.GlassStage
import sh.simple.android.ui.inbox.InboxAnswerAutoRetryEffect
import sh.simple.android.ui.inbox.InboxAnswerRequest
import sh.simple.android.ui.inbox.InboxItemCard
import sh.simple.android.ui.inbox.rememberInboxAnswerUiState
import sh.simple.android.ui.kit.AppText
import sh.simple.android.ui.kit.SecondaryButton
import sh.simple.android.ui.kit.AnchoredMenu
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import sh.simple.android.ui.kit.ComposerCard
import sh.simple.android.ui.kit.ComposerCardFields
import sh.simple.android.ui.composer.ComposerAttachmentRow
import sh.simple.android.ui.composer.ComposerSuggestionList
import sh.simple.android.ui.composer.ComposerSuggestionRow
import sh.simple.android.ui.composer.ComposerTokenState
import sh.simple.android.ui.composer.activeComposerToken
import sh.simple.android.ui.composer.applyComposerSuggestion
import sh.simple.android.ui.composer.filterFileSuggestions
import sh.simple.android.ui.composer.filterSlashSuggestions
import sh.simple.android.ui.composer.ComposerPlusMenu
import sh.simple.android.ui.composer.insertTranscript
import sh.simple.android.ui.composer.rememberComposerAttachmentPickers
import sh.simple.android.ui.composer.rememberComposerAttachmentState
import sh.simple.android.ui.composer.rememberComposerVoice
import sh.simple.android.ui.composer.ImageMarkupScreen
import sh.simple.android.voice.VoiceHistoryStore
import sh.simple.android.voice.VoiceModelStore
import sh.simple.android.voice.VoiceReplacementsStore
import sh.simple.android.ui.kit.ComposerOverlay
import sh.simple.android.ui.kit.ComposerPill
import sh.simple.android.ui.kit.composerChromeInset
import sh.simple.android.ui.kit.headerBarListTopPadding
import sh.simple.android.ui.kit.ShimmerText
import sh.simple.android.ui.util.HostReconnectReloadEffect
import sh.simple.android.ui.kit.agentNeighborKeyboardShortcuts
import sh.simple.android.ui.kit.rememberShortcutFocus
import sh.simple.android.ui.kit.rememberHardwareKeyboardAttached
import sh.simple.android.ui.kit.rememberHideKeyboard
import sh.simple.android.ui.kit.HeaderBar
import sh.simple.android.ui.kit.Icons
import sh.simple.android.ui.kit.MenuEntry
import sh.simple.android.ui.kit.EmptyState
import sh.simple.android.ui.kit.ScreenCard
import sh.simple.android.ui.util.AgentStatusLine
import sh.simple.android.ui.kit.WorkingIndicator
import sh.simple.android.ui.util.agentEntityStatusLine
import sh.simple.android.ui.agent.AgentSwipeHintLine
import sh.simple.android.ui.agent.agentSwipeChromeHeight
import sh.simple.android.ui.agent.agentSwipeGesture
import sh.simple.android.ui.agent.agentSwipeMotion
import sh.simple.android.ui.agent.agentSwipeNeighbors
import sh.simple.android.ui.agent.rememberAgentSwipeState
import sh.simple.android.ui.agents.AgentActionsHost
import sh.simple.android.ui.nav.Screen
import sh.simple.android.ui.theme.Tokens
import sh.simple.android.ui.theme.Type
import sh.simple.android.ui.util.agentContextLeftLabel
import sh.simple.android.ui.util.agentKindIcon
import sh.simple.android.ui.util.agentKindLabel
import sh.simple.android.ui.util.agentRuntimeLabel
import sh.simple.android.ui.util.agentWire
import sh.simple.android.ui.util.joinMetaParts
import sh.simple.android.ui.util.optionalComputerName
import sh.simple.android.ui.util.showPairedComputerNames

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    db: SimpleDatabase,
    hosts: Hosts,
    hostId: String,
    agentId: String,
    hostNames: Map<String, String>,
    onBack: () -> Unit,
    onOpenAgentScreen: () -> Unit,
    onOpenChat: (String, String) -> Unit,
    onSwapAgent: (String, String) -> Unit,
    onNavigate: (Screen) -> Unit,
    voiceStore: VoiceModelStore,
    voiceReplacements: VoiceReplacementsStore,
    voiceHistory: VoiceHistoryStore,
    modifier: Modifier = Modifier,
) {
    val agents by db.rememberAgents().collectAsState(initial = emptyList())
    val agent = agents.firstOrNull { it.hostId == hostId && it.id == agentId }
    val messages by db.rememberMessages(hostId, agentId).collectAsState(initial = emptyList())
    var decodedMessages by remember(hostId, agentId) { mutableStateOf<List<Message>>(emptyList()) }
    LaunchedEffect(hostId, agentId) {
        val seeded = decodeStoredMessagesAsync(db.messages().listForAgent(hostId, agentId))
        if (seeded.isNotEmpty()) decodedMessages = seeded
    }
    val messageBodies = remember(messages) { messages.map { it.json } }
    LaunchedEffect(messageBodies) {
        decodedMessages = decodeStoredMessagesAsync(messages)
    }
    val items by db.rememberItems().collectAsState(initial = emptyList())
    val openItems = items.mapNotNull { decodeItem(it) }.filter { it.agent == agentId && !it.resolved }
    val answerUi = rememberInboxAnswerUiState()
    val sent = answerUi.sent
    val answerErrors = answerUi.errors
    var menuOpen by remember { mutableStateOf(false) }
    var menuAnchor by remember { mutableStateOf(Rect.Zero) }
    var renameOpen by remember { mutableStateOf(false) }
    var killOpen by remember { mutableStateOf(false) }
    var removeOpen by remember { mutableStateOf(false) }
    var titleMenuOpen by remember { mutableStateOf(false) }
    var titleAnchor by remember { mutableStateOf(Rect.Zero) }
    var followUp by remember { mutableStateOf("") }
    var followUpField by remember { mutableStateOf(TextFieldValue("")) }
    var composerOpen by remember { mutableStateOf(false) }
    val agentCacheKey = "$hostId:$agentId"
    val warmCommands = remember(agentCacheKey) { Commands.peek(agentCacheKey)?.commands.orEmpty() }
    var commandCatalog by remember(agentCacheKey) { mutableStateOf(warmCommands) }
    var fileSuggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    val composerToken = activeComposerToken(followUpField)
    val searchCwd = agent?.cwd?.takeIf { it.isNotEmpty() } ?: agent?.workspace.orEmpty()
    LaunchedEffect(composerOpen, hostId, agentId) {
        if (!composerOpen) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            runCatching { Commands.load(hosts, hostId, agentId).commands }.getOrNull()?.let { commandCatalog = it }
        }
    }
    LaunchedEffect(composerToken, searchCwd) {
        val token = composerToken ?: return@LaunchedEffect
        if (token.kind != ComposerTokenState.Kind.File || searchCwd.isBlank()) return@LaunchedEffect
        delay(120)
        val live = activeComposerToken(followUpField) ?: return@LaunchedEffect
        if (live.kind != token.kind || live.query != token.query || live.start != token.start) return@LaunchedEffect
        val paths = withContext(Dispatchers.IO) {
            runCatching { RemoteGateway.filesSearch(hosts, hostId, searchCwd, token.query).paths }.getOrDefault(emptyList())
        }
        val after = activeComposerToken(followUpField)
        if (after?.kind == token.kind && after.query == token.query && after.start == token.start) {
            fileSuggestions = paths
        }
    }
    val suggestionRows = when (val token = composerToken) {
        null -> emptyList()
        else -> when (token.kind) {
            ComposerTokenState.Kind.Slash ->
                filterSlashSuggestions(commandCatalog.map { it.name to it.description }, token.query)
            ComposerTokenState.Kind.File -> filterFileSuggestions(fileSuggestions, token.query)
        }
    }
    var dismissedToken by remember { mutableStateOf<ComposerTokenState?>(null) }
    val visibleSuggestions = if (composerToken != null && composerToken == dismissedToken) emptyList() else suggestionRows
    BackHandler(composerOpen) { composerOpen = false }
    BackHandler(composerOpen && visibleSuggestions.isNotEmpty()) { dismissedToken = composerToken }
    val appContext = LocalContext.current
    val composerVoice = rememberComposerVoice(voiceStore, voiceReplacements, voiceHistory) { onNavigate(Screen.Voice) }
    val attachmentState = rememberComposerAttachmentState()
    val attachmentPickers = rememberComposerAttachmentPickers(attachmentState)
    var plusMenuOpen by remember { mutableStateOf(false) }
    var plusAnchor by remember { mutableStateOf(Rect.Zero) }
    var markupId by remember { mutableStateOf<String?>(null) }
    var viewerPath by remember { mutableStateOf<String?>(null) }
    val imageRows by db.messageImages().observeForAgent(hostId, agentId).collectAsState(initial = emptyList())
    val imagesByMessage = remember(imageRows) {
        imageRows.groupBy { it.messageId }.mapValues { entry ->
            entry.value.sortedBy { it.slot }.map { it.localPath }
        }
    }
    LaunchedEffect(composerVoice.transcriptReady) {
        val words = composerVoice.consumeTranscript() ?: return@LaunchedEffect
        followUpField = insertTranscript(followUpField, words)
        followUp = followUpField.text
    }
    LaunchedEffect(voiceHistory.pendingInsert) {
        val words = voiceHistory.consumePendingInsert() ?: return@LaunchedEffect
        followUpField = insertTranscript(followUpField, words)
        followUp = followUpField.text
        composerOpen = true
    }
    LaunchedEffect(attachmentState.attachments.size) {
        if (attachmentState.attachments.isNotEmpty()) composerOpen = true
    }
    var modelOpen by remember { mutableStateOf(false) }
    var modelLabel by remember { mutableStateOf("Model") }
    var modeMenuOpen by remember { mutableStateOf(false) }
    var modeAnchor by remember { mutableStateOf(Rect.Zero) }
    var modeLabel by remember { mutableStateOf("Ask") }
    var modeOptions by remember { mutableStateOf(listOf("ask", "auto", "full")) }
    var currentMode by remember { mutableStateOf("ask") }
    val hideKeyboard = rememberHideKeyboard()
    LaunchedEffect(composerOpen, modelOpen, modeMenuOpen, plusMenuOpen) {
        if (composerOpen && (modelOpen || modeMenuOpen || plusMenuOpen)) hideKeyboard()
    }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var stickToBottom by remember(hostId, agentId) { mutableStateOf(true) }
    LaunchedEffect(listState) {
        var userScrolled = false
        launch {
            listState.interactionSource.interactions.collect { interaction ->
                if (interaction is DragInteraction.Start) {
                    userScrolled = true
                    stickToBottom = false
                }
            }
        }
        snapshotFlow { listState.isScrollInProgress }.drop(1).collect { scrolling ->
            if (!scrolling && userScrolled) {
                userScrolled = false
                stickToBottom = !listState.canScrollForward
            }
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.canScrollForward to listState.layoutInfo.totalItemsCount }.collect { (canForward, count) ->
            if (canForward && stickToBottom && count > 0 && !listState.isScrollInProgress) {
                listState.scrollToItem(count - 1, Int.MAX_VALUE / 2)
            }
        }
    }
    val app = LocalContext.current.applicationContext as App
    InboxAnswerAutoRetryEffect(hosts, hostId, answerUi) { itemId ->
        app.inboxNotifications.cancel(itemId)
    }
    var transcriptFetched by remember(hostId, agentId) { mutableStateOf(false) }
    LaunchedEffect(hostId, agentId) {
        withContext(Dispatchers.IO) { syncAgentTranscript(hosts, db, hostId, agentId) }
        transcriptFetched = true
    }
    HostReconnectReloadEffect(hosts, hostId) {
        scope.launchRpc { RemoteGateway.watch(hosts, hostId, agentId, true) }
        scope.launch(Dispatchers.IO) {
            syncAgentTranscript(hosts, db, hostId, agentId)
            transcriptFetched = true
        }
    }
    ChatFollowUpAutoRetryEffect(hosts, hostId, app.chatOutbound, app.vault, db)
    var hostLive by remember(hostId) { mutableStateOf(false) }
    LaunchedEffect(hostId) {
        val link = hosts.link(hostId) ?: return@LaunchedEffect
        link.state.collect { next -> hostLive = next is HostLinkState.Live }
    }
    LaunchedEffect(hostId, agent?.kind, agent?.json) {
        val kind = agent?.kind ?: return@LaunchedEffect
        val wire = decodeAgent(agent?.json)
        currentMode = wire?.mode?.lowercase() ?: "ask"
        modeLabel = modeMenuLabel(currentMode)
        val label = withContext(Dispatchers.IO) {
            runCatching {
                val catalog = Models.load(hosts, db, hostId, kind)
                if (catalog.modes.isNotEmpty()) modeOptions = catalog.modes
                modelDisplayLabel(catalog, wire?.model)
            }.getOrElse { wire?.model ?: "Model" }
        }
        modelLabel = label
    }
    val working = agent?.status == "working"
    val failed = agent?.status == "failed"
    val followUpPlaceholder = if (attachmentState.attachments.isEmpty()) "Follow up..." else "Plan, ask, build..."
    val outboundKey = "$hostId:$agentId"
    val outbound = app.chatOutbound.pending[outboundKey]
    val queuedOutbound = app.chatOutbound.queued[outboundKey].orEmpty()
    val workingSinceMs = remember(decodedMessages, outbound, agent?.updated, working) {
        workingIndicatorSinceMs(decodedMessages, outbound, agent?.updated ?: 0L, working)
    }
    var workingAnnouncement by remember { mutableStateOf<String?>(null) }
    var wasWorking by remember { mutableStateOf(false) }
    LaunchedEffect(working) {
        if (working) {
            if (!wasWorking) workingAnnouncement = "Working"
            wasWorking = true
        } else if (wasWorking) {
            workingAnnouncement = "Done"
            delay(1200)
            workingAnnouncement = null
            wasWorking = false
        }
    }
    ChatOpenTraceEffect(decodedMessages.isNotEmpty() || outbound != null)
    val turnMessages = remember(decodedMessages) { decodedMessages.filter { !it.id.startsWith("sf:") } }
    val blocks = remember(turnMessages, working) { chatBlocks(turnMessages, working) }
    LaunchedEffect(decodedMessages) {
        val userTexts = decodedMessages.filter { it.role == "user" }.mapNotNull { it.text }
        val cleared = app.chatOutbound.reconcile(hostId, agentId, userTexts)
        if (cleared != null) {
            val matchedId = decodedMessages
                .filter { it.role == "user" }
                .drop(cleared.userLinesBefore)
                .lastOrNull { msg ->
                    msg.text != null && normalizeUserMatchText(msg.text!!) == normalizeUserMatchText(cleared.displayText)
                }?.id
            if (matchedId != null) {
                app.chatOutbound.rememberUserKey(matchedId, cleared.localId)
                rebindPendingMessageImages(db.messageImages(), hostId, agentId, cleared.localId, matchedId)
            }
        }
    }
    val displayBlocks = remember(blocks, outbound, queuedOutbound, imagesByMessage, working, agent?.updated) {
        val store = app.chatOutbound
        val enriched = blocks.map { block ->
            when (block) {
                is ChatBlock.User if !block.outbound -> {
                    block.copy(
                        key = store.displayUserKey(block.key),
                        text = stripImageMarkers(block.text),
                        imagePaths = imagesByMessage[block.key].orEmpty(),
                    )
                }
                else -> block
            }
        }
        val tail = enriched.lastOrNull() as? ChatBlock.User
        val serverAlreadyShowsOutbound = outbound != null && tail != null && !tail.outbound &&
            normalizeUserMatchText(tail.text) == normalizeUserMatchText(outbound.displayText)
        fun bubble(row: OutboundChatBubble, label: String?) = ChatBlock.User(
            key = row.localId,
            text = row.displayText,
            imagePaths = row.imagePaths.ifEmpty { imagesByMessage["pending:${row.localId}"].orEmpty() },
            outbound = true,
            statusLabel = label,
        )
        val waitingRows = queuedOutbound.map { bubble(it, if (it.waitingForLink) "Sending when back" else "Queued") }
        if (outbound == null || serverAlreadyShowsOutbound) {
            enriched + waitingRows
        } else {
            val label = when {
                outbound.waitingForLink -> "Sending when back"
                queuedOutbound.isNotEmpty() || outboundIsQueued(working, agent?.updated ?: 0L, outbound.stagedAtMs) -> "Queued"
                else -> null
            }
            enriched + waitingRows + bubble(outbound, label)
        }
    }
    val awaitingReply = chatAwaitingReply(working, outbound, agent?.updated ?: 0L, failed)
    val turns = remember(displayBlocks, awaitingReply) { splitChatTurns(displayBlocks, awaitingReply) }
    var openTurns by remember { mutableStateOf(emptySet<String>()) }
    val turnSummaries = remember(turns) {
        turns.filter { !it.live && turnHasAgentWork(it.body) }.associate { it.key to summarizeTurn(it) }
    }
    val turnRows = remember(turns, turnSummaries, openTurns) { chatTurnRows(turns, turnSummaries, openTurns) }
    val browsePath = agent?.workspace?.takeIf { it.isNotEmpty() } ?: agent?.cwd.orEmpty()
    val liveToolName = remember(displayBlocks, working) {
        if (!working) return@remember null
        when (val last = displayBlocks.lastOrNull()) {
            is ChatBlock.Tools -> last.tools.lastOrNull { it.running }?.title
            else -> null
        }
    }
    val imeVisible = WindowInsets.isImeVisible
    var composerBottomPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val composerBottom = with(density) { composerBottomPx.toDp() }
    val listBottomPadding = PaddingValues(bottom = composerBottom + imeBottom + 12.dp)
    LaunchedEffect(turnRows, working, composerOpen, imeVisible, composerBottomPx, outbound, displayBlocks.size) {
        if (stickToBottom && (displayBlocks.isNotEmpty() || outbound != null)) {
            withFrameNanos { }
            val last = listState.layoutInfo.totalItemsCount - 1
            if (last >= 0) listState.scrollToItem(last, Int.MAX_VALUE / 2)
        }
    }
    val headerTopPad = headerBarListTopPadding()
    val listContentPadding = PaddingValues(
        top = headerTopPad.calculateTopPadding() + 12.dp,
        bottom = listBottomPadding.calculateBottomPadding(),
    )
    val swipeState = rememberAgentSwipeState()
    val (_, swipePrev, swipeNext) = agentSwipeNeighbors(agents, hostId, agentId)
    val swipeEnabled = swipePrev != null || swipeNext != null
    val swipeCommit: (sh.simple.android.ui.agent.AgentSwipeNeighbor) -> Unit = { n -> onSwapAgent(n.hostId, n.agentId) }
    LaunchedEffect(hostId, agentId) { swipeState.resetDrag() }
    val hardwareKb = rememberHardwareKeyboardAttached()
    var peekText by remember { mutableStateOf("") }
    val showPeek = agent != null && chatShowsScreenPeek(agent.kind)
    DisposableEffect(hostId, agentId) {
        scope.launchRpc { RemoteGateway.watch(hosts, hostId, agentId, true) }
        onDispose { scope.launchRpc { RemoteGateway.watch(hosts, hostId, agentId, false) } }
    }
    LaunchedEffect(hostId, agentId, showPeek) {
        if (!showPeek) return@LaunchedEffect
        hosts.events(hostId)?.filter { it.ev == "screen" }?.collect { ev ->
            val payload = WireJson.decodeFromJsonElement(ScreenPayload.serializer(), ev.d)
            if (payload.id == agentId) peekText = payload.text
        }
    }
    GlassStage(
        modifier
            .fillMaxSize()
            .background(Tokens.current.background)
            .agentNeighborKeyboardShortcuts(
                true,
                onPrev = { swipePrev?.let { swipeCommit(it) } },
                onNext = { swipeNext?.let { swipeCommit(it) } },
            )
            .then(rememberShortcutFocus(composerOpen)),
        chrome = {
            Box(
                Modifier
                    .align(Alignment.TopStart)
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
                    left = listOf(Icons.Back),
                    title = agent?.title ?: "Agent",
                    right = listOf(Icons.Grid, Icons.Term, Icons.More),
                    subtitleContent = {
                        agent?.let { row ->
                            ChatHeaderSubtitle(
                                if (openItems.any { it.kind != "done" }) {
                                    sh.simple.android.ui.util.AgentStatusLine("Waiting for you", sh.simple.android.ui.util.AgentStatusTone.Attention, shimmer = false)
                                } else {
                                    agentEntityStatusLine(row, if (working) liveToolName else null)
                                },
                                chatHeaderContext(row, hostNames, hostLive, showPairedComputerNames(hostNames.size)),
                            )
                        }
                    },
                    titleIcon = agent?.kind?.let { agentKindIcon(it) },
                    titleIconDescription = agent?.kind?.let { agentKindLabel(it) },
                    onLeftClick = { _ -> onBack() },
                    onTitleLongClick = { titleMenuOpen = true },
                    onTitleBounds = { titleAnchor = it },
                    onRightBounds = { index, bounds -> if (index == 2) menuAnchor = bounds },
                    onRightClick = { index ->
                        when (index) {
                            0 -> {
                                val ws = agent?.workspace?.takeIf { it.isNotEmpty() } ?: agent?.cwd.orEmpty()
                                if (ws.isNotEmpty()) onNavigate(Screen.Panes(hostId, ws, agentId))
                            }
                            1 -> onOpenAgentScreen()
                            2 -> menuOpen = true
                        }
                    },
                )
                if (swipeEnabled) AgentSwipeHintLine()
            }
            ChatOpenTraceOverlay(Modifier.align(Alignment.TopEnd))
            if (composerOpen) {
                ComposerOverlay(onDismiss = { composerOpen = false }) {
                    Box(Modifier.onSizeChanged { composerBottomPx = it.height }) {
                    ComposerCardFields(
                        chips = {},
                        placeholder = followUpPlaceholder,
                        field = followUpField,
                        onFieldChange = {
                            followUpField = it
                            followUp = it.text
                        },
                        modeLabel = modeLabel,
                        modelLabel = modelLabel,
                        sending = composerShowsStop(working, followUp, attachmentState.attachments.isNotEmpty()),
                        onPlus = { plusMenuOpen = true },
                        plusModifier = Modifier.onGloballyPositioned { plusAnchor = it.boundsInRoot() },
                        attachmentRow = {
                            ComposerAttachmentRow(
                                attachmentState,
                                onRemove = { attachmentState.remove(it) },
                                onOpen = { markupId = it.id },
                            )
                        },
                        onMic = { composerVoice.onMic(appContext) },
                        voicePhase = composerVoice.phase,
                        voicePermissionHint = composerVoice.permissionHint,
                        onVoiceCancel = { composerVoice.cancel() },
                        onModeClick = { modeMenuOpen = true },
                        modeChipModifier = Modifier.onGloballyPositioned { modeAnchor = it.boundsInRoot() },
                        onModelClick = { modelOpen = true },
                        suggestionRow = {
                            ComposerSuggestionList(
                                rows = visibleSuggestions,
                                onPick = { row ->
                                    val token = composerToken ?: return@ComposerSuggestionList
                                    val value = when (row) {
                                        is ComposerSuggestionRow.Slash -> row.name
                                        is ComposerSuggestionRow.File -> row.path
                                    }
                                    followUpField = applyComposerSuggestion(followUpField, token, value)
                                    followUp = followUpField.text
                                },
                            )
                        },
                        onSend = {
                            val text = followUp.trim()
                            if (text.isEmpty() && attachmentState.attachments.isEmpty()) return@ComposerCardFields
                            val items = attachmentState.attachments.toList()
                            val attachPaths = items.map { it.localPath }
                            val userBefore = messages.count { it.role == "user" }
                            val ws = agent?.workspace?.takeIf { it.isNotEmpty() } ?: agent?.cwd.orEmpty()
                            stickToBottom = true
                            val localId = app.chatOutbound.stage(hostId, agentId, text, attachPaths, userBefore)
                            app.chatOutbound.rememberDelivery(hostId, agentId, text, attachPaths, ws)
                            followUp = ""
                            followUpField = TextFieldValue("")
                            fileSuggestions = emptyList()
                            attachmentState.clear()
                            composerOpen = false
                            scope.launch(Dispatchers.IO) {
                                persistMessageImages(
                                    appContext,
                                    db.messageImages(),
                                    hostId,
                                    agentId,
                                    localId,
                                    attachPaths,
                                )
                            }
                            scope.launchChatFollowUp(
                                hosts,
                                app.vault,
                                db,
                                app.chatOutbound,
                                hostId,
                                agentId,
                                text,
                                attachPaths,
                                ws,
                            )
                        },
                        onStop = {
                            scope.launchRpc { RemoteGateway.interrupt(hosts, hostId, agentId) }
                        },
                        hardwareKeyboard = hardwareKb,
                        onHardwareEnter = {
                            val text = followUp.trim()
                            if (text.isNotEmpty() || attachmentState.attachments.isNotEmpty()) {
                                val items = attachmentState.attachments.toList()
                                val attachPaths = items.map { it.localPath }
                                val userBefore = messages.count { it.role == "user" }
                                val ws = agent?.workspace?.takeIf { it.isNotEmpty() } ?: agent?.cwd.orEmpty()
                                stickToBottom = true
                                val localId = app.chatOutbound.stage(hostId, agentId, text, attachPaths, userBefore)
                                app.chatOutbound.rememberDelivery(hostId, agentId, text, attachPaths, ws)
                                followUp = ""
                                followUpField = TextFieldValue("")
                                fileSuggestions = emptyList()
                                attachmentState.clear()
                                composerOpen = false
                                scope.launch(Dispatchers.IO) {
                                    persistMessageImages(
                                        appContext,
                                        db.messageImages(),
                                        hostId,
                                        agentId,
                                        localId,
                                        attachPaths,
                                    )
                                }
                                scope.launchChatFollowUp(
                                    hosts,
                                    app.vault,
                                    db,
                                    app.chatOutbound,
                                    hostId,
                                    agentId,
                                    text,
                                    attachPaths,
                                    ws,
                                )
                            }
                        },
                        onHardwareEscape = {
                            if (visibleSuggestions.isNotEmpty()) {
                                dismissedToken = composerToken
                            } else {
                                composerOpen = false
                            }
                        },
                    )
                    }
                }
            } else {
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .onSizeChanged { composerBottomPx = it.height },
                ) {
                    if (swipeEnabled) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(bottom = 68.dp)
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
                    ComposerPill(
                        if (working) "Queue a message..." else followUpPlaceholder,
                        onPlus = { plusMenuOpen = true },
                        onMic = {
                            if (working) {
                                scope.launchRpc { RemoteGateway.interrupt(hosts, hostId, agentId) }
                            } else {
                                composerOpen = true
                                composerVoice.onMic(appContext)
                            }
                        },
                        trailingIcon = if (working) Icons.Stop else Icons.Mic,
                        trailingIconDescription = if (working) "Stop agent" else "Voice input",
                        onTap = { composerOpen = true },
                        plusModifier = Modifier.onGloballyPositioned { plusAnchor = it.boundsInRoot() },
                        modifier = Modifier.composerChromeInset(),
                    )
                }
            }
        },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .agentSwipeMotion(swipeState)
                .onGloballyPositioned { swipeState.onWidthMeasured(it.size.width.toFloat()) },
        ) {
            LazyColumn(
                state = listState,
                contentPadding = listContentPadding,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
            ) {
                lazyItems(turnRows, key = { it.key }) { row ->
                    TurnRowView(
                        row,
                        agentKind = agent?.kind ?: "claude",
                        displayBlocks = displayBlocks,
                        working = working,
                        copyModifier = { Modifier.copyOnLongPress(it) },
                        onOpenImage = { viewerPath = it },
                        onToggleWorked = { key -> openTurns = if (key in openTurns) openTurns - key else openTurns + key },
                        onReviewChanges = {
                            if (browsePath.isNotEmpty()) onNavigate(Screen.Changes(hostId, browsePath))
                        },
                    )
                }
                lazyItems(openItems, key = { it.id }) { item ->
                    InboxItemCard(
                        item,
                        sent[item.id] == true,
                        Modifier.padding(vertical = 8.dp),
                        error = answerErrors[item.id].orEmpty(),
                    ) { decision, option, text ->
                        scope.launch {
                            answerUi.submit(
                                hosts,
                                hostId,
                                item.id,
                                InboxAnswerRequest(decision, option, text),
                                onSuccess = { app.inboxNotifications.cancel(item.id) },
                            )
                        }
                    }
                }
                if (showPeek) {
                    item {
                        ScreenCard(
                            peekTail(peekText),
                            Modifier.padding(vertical = 8.dp),
                            onClick = onOpenAgentScreen,
                        )
                    }
                }
                if (awaitingReply && displayBlocks.lastOrNull() !is ChatBlock.Tools && displayBlocks.lastOrNull() !is ChatBlock.Thinking) {
                    item(key = "working") {
                        val phase = when {
                            outbound != null && !outbound.daemonAcked && !working -> "Sending"
                            displayBlocks.none { it is ChatBlock.User } && outbound == null -> "Starting"
                            else -> "Thinking"
                        }
                        Box(
                            Modifier.semantics {
                                liveRegion = LiveRegionMode.Polite
                                workingAnnouncement?.let { contentDescription = it }
                            },
                        ) {
                            WorkingIndicator(
                                liveToolName,
                                workingSinceMs,
                                detail = phase,
                            )
                        }
                    }
                }
                if (transcriptFetched && displayBlocks.isEmpty() && outbound == null && !awaitingReply && openItems.isEmpty() && !showPeek && agent?.status != "failed") {
                    item(key = "empty") {
                        EmptyState(
                            Icons.Term,
                            "No messages yet",
                            "Send a message from the composer below.",
                        )
                    }
                }
                if (failed) {
                    item(key = "send-failed") {
                        val userLines = decodedMessages.filter { it.role == "user" }.mapNotNull { it.text }
                        val showRetry = sendFailureRetryVisible(true, outbound, userLines)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppText(
                                sendFailureLine(decodedMessages),
                                Modifier.weight(1f),
                                style = Type.meta,
                                color = Tokens.current.muted,
                            )
                            if (showRetry) {
                                SecondaryButton("Retry", Modifier.padding(start = 12.dp)) {
                                    val row = outbound ?: return@SecondaryButton
                                    val ws = agent?.workspace?.takeIf { it.isNotEmpty() } ?: agent?.cwd.orEmpty()
                                    scope.launchChatFollowUp(
                                        hosts,
                                        app.vault,
                                        db,
                                        app.chatOutbound,
                                        hostId,
                                        agentId,
                                        row.displayText,
                                        row.imagePaths,
                                        ws,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    AnchoredMenu(
        open = menuOpen,
        onDismiss = { menuOpen = false },
        anchorBounds = menuAnchor,
        items = listOf(
            MenuEntry("Usage", Icons.Bolt) { onNavigate(Screen.Usage(hostId)) },
            MenuEntry("Servers", Icons.Server) { onNavigate(Screen.Servers(hostId)) },
            MenuEntry("Model", Icons.Sliders) { modelOpen = true },
            MenuEntry("Files", Icons.Folder) {
                if (browsePath.isNotEmpty()) onNavigate(Screen.Files(hostId, browsePath))
            },
            MenuEntry("Changes", Icons.Branch) {
                if (browsePath.isNotEmpty()) onNavigate(Screen.Changes(hostId, browsePath))
            },
        ),
    )
    AgentActionsHost(
        hosts = hosts,
        hostId = hostId,
        agent = agent,
        menuOpen = titleMenuOpen,
        onMenuDismiss = { titleMenuOpen = false },
        anchor = titleAnchor,
        renameOpen = renameOpen,
        onRenameOpenChange = { renameOpen = it },
        killOpen = killOpen,
        onKillOpenChange = { killOpen = it },
        removeOpen = removeOpen,
        onRemoveOpenChange = { removeOpen = it },
        onRemoved = onBack,
    )
    ModelPickerSheets(
        open = modelOpen,
        onDismiss = { modelOpen = false },
        hosts = hosts,
        db = db,
        hostId = hostId,
        kind = agent?.kind ?: "claude",
        agentId = agentId,
        currentModel = decodeAgent(agent?.json)?.model,
        currentEffort = decodeAgent(agent?.json)?.effort,
    )
    PermissionModeMenu(
        open = modeMenuOpen,
        onDismiss = { modeMenuOpen = false },
        anchorBounds = modeAnchor,
        modes = modeOptions,
        current = currentMode,
        onPick = { picked ->
            currentMode = picked.lowercase()
            modeLabel = modeMenuLabel(currentMode)
            scope.launchRpc {
                RemoteGateway.configure(hosts, hostId, agentId, mode = picked)
            }
        },
    )
    ComposerPlusMenu(
        open = plusMenuOpen,
        onDismiss = { plusMenuOpen = false },
        anchor = plusAnchor,
        onPhoto = attachmentPickers.openPhoto,
        onCamera = attachmentPickers.openCamera,
    )
    val viewer = viewerPath
    if (viewer != null) {
        ChatImageViewer(viewer) { viewerPath = null }
    }
    val markupItem = attachmentState.attachments.firstOrNull { it.id == markupId }
    if (markupItem != null) {
        ImageMarkupScreen(
            attachment = markupItem,
            onDone = {
                attachmentState.markMarkup(markupItem.id)
                markupId = null
            },
            onCancel = { markupId = null },
        )
    }
}

@Composable
private fun ChatHeaderSubtitle(line: AgentStatusLine, context: String) {
    val muted = Tokens.current.muted
    val suffix = buildString {
        if (line.workingTail.isNotEmpty()) append(line.workingTail.removePrefix(" · "))
        if (context.isNotEmpty()) {
            if (isNotEmpty()) append(" · ")
            append(context)
        }
    }
  if (line.shimmer) {
        androidx.compose.foundation.layout.Row {
            ShimmerText("Working", style = Type.caption, baseColor = muted)
            if (suffix.isNotEmpty()) {
                AppText(
                    " · $suffix",
                    style = Type.caption,
                    color = muted,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
    } else {
        val text = if (suffix.isEmpty()) line.text else "${line.text} · $suffix"
        AppText(
            text,
            style = Type.caption,
            color = muted,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}

private fun chatHeaderContext(
    agent: sh.simple.android.store.AgentEntity,
    hostNames: Map<String, String>,
    hostLive: Boolean,
    showComputer: Boolean,
): String {
    val wire = agentWire(agent)
    val base = joinMetaParts(
        listOf(
            agentKindLabel(agent.kind),
            agentRuntimeLabel(wire?.runtime ?: "hc"),
            agentContextLeftLabel(wire?.context),
            agent.workspace.substringAfterLast('/').takeIf { it.isNotEmpty() },
            optionalComputerName(agent.hostId, hostNames, showComputer),
        ),
    )
    if (hostLive) return base
    return if (base.isEmpty()) "Offline" else "Offline · $base"
}

private fun peekTail(text: String): String {
    if (text.isBlank()) return "Waiting for screen..."
    return text.lines().takeLast(8).joinToString("\n")
}

private fun decodeAgent(json: String?): Agent? {
    if (json.isNullOrBlank()) return null
    return runCatching { WireJson.decodeFromString<Agent>(json) }.getOrNull()
}

private fun decodeMessage(json: String): Message? = runCatching { WireJson.decodeFromString<Message>(json) }.getOrNull()
private fun decodeItem(row: sh.simple.android.store.ItemEntity): Item? =
    runCatching { WireJson.decodeFromString<Item>(row.json) }.getOrNull()

private fun Modifier.copyOnLongPress(text: String): Modifier = composed {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    pointerInput(text) {
        detectTapGestures(onLongPress = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("message", text))
            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
        })
    }
}
