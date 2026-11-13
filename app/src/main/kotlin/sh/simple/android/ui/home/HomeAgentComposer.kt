package sh.simple.android.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sh.simple.android.App
import sh.simple.android.link.Hosts
import sh.simple.android.link.RemoteGateway
import sh.simple.android.store.Models
import sh.simple.android.store.SimpleDatabase
import sh.simple.android.store.modelDisplayLabel
import sh.simple.android.store.rememberAgents
import sh.simple.android.store.rememberHostsForDisplay
import sh.simple.android.store.rememberWorkspaces
import sh.simple.android.store.resetAgentDraftForCatalog
import sh.simple.android.ui.composer.AgentKindPickerSheet
import sh.simple.android.ui.composer.ComposerAttachment
import sh.simple.android.ui.composer.ComposerAttachmentRow
import sh.simple.android.ui.composer.ComposerPlusMenu
import sh.simple.android.ui.composer.FolderListCache
import sh.simple.android.ui.composer.ImageMarkupScreen
import sh.simple.android.ui.composer.NewAgentConfigureSheet
import sh.simple.android.ui.composer.existingPathsAmong
import sh.simple.android.ui.composer.homeWorkspacesVisible
import sh.simple.android.ui.composer.insertTranscript
import sh.simple.android.ui.composer.recentDirectoriesForHost
import sh.simple.android.ui.composer.rememberComposerAttachmentPickers
import sh.simple.android.ui.composer.rememberComposerAttachmentState
import sh.simple.android.ui.composer.rememberComposerVoice
import sh.simple.android.ui.composer.targetsFromWorkspaces
import sh.simple.android.ui.kit.ComposerCardFields
import sh.simple.android.ui.kit.ComposerOverlay
import sh.simple.android.ui.kit.GlassChip
import sh.simple.android.ui.kit.Icons
import sh.simple.android.ui.kit.rememberHardwareKeyboardAttached
import sh.simple.android.ui.kit.rememberHideKeyboard
import sh.simple.android.ui.model.ModelPickerSheets
import sh.simple.android.ui.model.PermissionModeMenu
import sh.simple.android.ui.model.modeMenuLabel
import sh.simple.android.ui.nav.Screen
import sh.simple.android.ui.util.agentKindIcon
import sh.simple.android.ui.util.agentKindLabel
import sh.simple.android.ui.util.installedNewAgentKinds
import sh.simple.android.ui.util.showPairedComputerNames
import sh.simple.android.voice.VoiceHistoryStore
import sh.simple.android.voice.VoiceModelStore
import sh.simple.android.voice.VoiceReplacementsStore

@Composable
internal fun HomeAgentComposerLayer(
    db: SimpleDatabase,
    hosts: Hosts,
    onNavigate: (Screen) -> Unit,
    voiceStore: VoiceModelStore,
    voiceReplacements: VoiceReplacementsStore,
    voiceHistory: VoiceHistoryStore,
    composerOpen: Boolean,
    onComposerOpenChange: (Boolean) -> Unit,
    searchOpen: Boolean,
    onSearchOpenChange: (Boolean) -> Unit,
    computersOpen: Boolean,
    onComputersOpenChange: (Boolean) -> Unit,
    onComputersDismiss: () -> Unit,
    openComputers: Boolean,
    onComputersOpened: () -> Unit,
) {
    val hostRows by db.rememberHostsForDisplay().collectAsState(initial = emptyList())
    val agents by db.rememberAgents().collectAsState(initial = emptyList())
    val workspaces by db.rememberWorkspaces().collectAsState(initial = emptyList())
  LaunchedEffect(openComputers) {
        if (openComputers) {
            onComputersOpenChange(true)
            onComputersOpened()
        }
    }
    BackHandler(composerOpen) { onComposerOpenChange(false) }
    var composerText by remember { mutableStateOf("") }
    var composerField by remember { mutableStateOf(TextFieldValue("")) }
    val appContext = LocalContext.current
    val app = appContext.applicationContext as App
    val composerVoice = rememberComposerVoice(voiceStore, voiceReplacements, voiceHistory) { onNavigate(Screen.Voice) }
    val attachmentState = rememberComposerAttachmentState()
    val attachmentPickers = rememberComposerAttachmentPickers(attachmentState)
    var plusMenuOpen by remember { mutableStateOf(false) }
    var plusAnchor by remember { mutableStateOf(Rect.Zero) }
    var markupId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(composerVoice.transcriptReady) {
        val words = composerVoice.consumeTranscript() ?: return@LaunchedEffect
        composerField = insertTranscript(composerField, words)
        composerText = composerField.text
    }
    LaunchedEffect(voiceHistory.pendingInsert) {
        val words = voiceHistory.consumePendingInsert() ?: return@LaunchedEffect
        composerField = insertTranscript(composerField, words)
        composerText = composerField.text
        onComposerOpenChange(true)
    }
    var modelOpen by remember { mutableStateOf(false) }
    var draftKind by remember { mutableStateOf("claude") }
    var agentPickerOpen by remember { mutableStateOf(false) }
    var installedKinds by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var draftModel by remember { mutableStateOf<String?>("opus") }
    var draftEffort by remember { mutableStateOf("high") }
    var draftMode by remember { mutableStateOf("ask") }
    var modelLabel by remember { mutableStateOf("Model") }
    var modeLabel by remember { mutableStateOf("Ask") }
    var modeMenuOpen by remember { mutableStateOf(false) }
    var modeAnchor by remember { mutableStateOf(Rect.Zero) }
    var modeOptions by remember { mutableStateOf(listOf("ask", "plan", "auto", "full")) }
    var newAgentOpen by remember { mutableStateOf(false) }
    var newAgentPrompt by remember { mutableStateOf("") }
    var newAgentAttachments by remember { mutableStateOf<List<ComposerAttachment>>(emptyList()) }
    val defaultHostId = hostRows.firstOrNull()?.id.orEmpty()
    val hardwareKb = rememberHardwareKeyboardAttached()
    var workspaceExistence by remember { mutableStateOf<Set<String>?>(null) }
    LaunchedEffect(defaultHostId, workspaces, agents) {
        if (defaultHostId.isEmpty()) {
            workspaceExistence = null
            return@LaunchedEffect
        }
        val paths = workspaces.filter { it.hostId == defaultHostId }.map { it.path }
        workspaceExistence = if (paths.isEmpty()) {
            emptySet()
        } else {
            existingPathsAmong(hosts, defaultHostId, paths)
        }
    }
    LaunchedEffect(defaultHostId) {
        if (defaultHostId.isEmpty()) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            runCatching {
                val listed = RemoteGateway.filesList(hosts, defaultHostId, "~").entries
                FolderListCache.put(defaultHostId, "~", listed)
            }
        }
    }
    val visibleWorkspaces = remember(workspaces, agents, workspaceExistence) {
        val assumeExist = workspaceExistence ?: workspaces.map { it.path }.toSet()
        homeWorkspacesVisible(workspaces, agents, assumeExist)
    }
    val showComputer = showPairedComputerNames(hostRows.size)
    val agentTargets = targetsFromWorkspaces(
        hostRows.associate { it.id to it.name },
        visibleWorkspaces,
        defaultHostId,
        showComputer,
    )
    val hideKeyboard = rememberHideKeyboard()
    LaunchedEffect(newAgentOpen, modelOpen, modeMenuOpen, plusMenuOpen, agentPickerOpen) {
        if (newAgentOpen || modelOpen || modeMenuOpen || plusMenuOpen || agentPickerOpen) hideKeyboard()
    }
    LaunchedEffect(defaultHostId) {
        if (defaultHostId.isEmpty()) return@LaunchedEffect
        for (attempt in 0 until 10) {
            val kinds = withContext(Dispatchers.IO) {
                runCatching { RemoteGateway.installedKinds(hosts, defaultHostId).kinds }.getOrDefault(emptyMap())
            }
            if (kinds.isNotEmpty()) {
                installedKinds = kinds
                break
            }
            kotlinx.coroutines.delay(2000)
        }
        val usable = installedNewAgentKinds(installedKinds)
        if (draftKind !in usable) usable.firstOrNull()?.let { draftKind = it }
    }
    LaunchedEffect(defaultHostId, draftKind) {
        if (defaultHostId.isEmpty()) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            runCatching {
                val reset = resetAgentDraftForCatalog(Models.load(hosts, db, defaultHostId, draftKind), draftKind, draftModel, draftMode)
                draftModel = reset.model
                draftMode = reset.mode
                modeLabel = reset.modeLabel
                modeOptions = reset.modeOptions
            }
        }
    }
    LaunchedEffect(defaultHostId, draftKind, draftModel) {
        if (defaultHostId.isEmpty()) return@LaunchedEffect
        modelLabel = withContext(Dispatchers.IO) {
            runCatching {
                val catalog = Models.load(hosts, db, defaultHostId, draftKind)
                if (catalog.modes.isNotEmpty()) modeOptions = catalog.modes
                modelDisplayLabel(catalog, draftModel)
            }.getOrDefault("Model")
        }
    }
    if (composerOpen) {
        ComposerOverlay(onDismiss = { onComposerOpenChange(false) }) {
            ComposerCardFields(
                chips = {
                    agentTargets.firstOrNull()?.let { target ->
                        GlassChip(target.label, leadingIcon = Icons.Folder, onClick = { newAgentOpen = true })
                    }
                    if (defaultHostId.isNotEmpty()) {
                        GlassChip(agentKindLabel(draftKind), leadingIcon = agentKindIcon(draftKind), onClick = { agentPickerOpen = true })
                    }
                },
                field = composerField,
                onFieldChange = {
                    composerField = it
                    composerText = it.text
                },
                modeLabel = modeLabel,
                modelLabel = modelLabel,
                sending = false,
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
                modeChipModifier = Modifier.onGloballyPositioned { coords ->
                    val topLeft = coords.localToRoot(Offset.Zero)
                    val size = coords.size
                    modeAnchor = Rect(
                        topLeft.x,
                        topLeft.y,
                        topLeft.x + size.width,
                        topLeft.y + size.height,
                    )
                },
                onModelClick = { if (defaultHostId.isNotEmpty()) modelOpen = true },
                onSend = {
                    if (composerText.trim().isEmpty() && attachmentState.attachments.isEmpty()) {
                        onComposerOpenChange(false)
                    } else {
                        newAgentPrompt = composerText.trim()
                        newAgentAttachments = attachmentState.attachments.toList()
                        composerText = ""
                        composerField = TextFieldValue("")
                        attachmentState.clear()
                        onComposerOpenChange(false)
                        newAgentOpen = true
                    }
                },
                onStop = { onComposerOpenChange(false) },
                hardwareKeyboard = hardwareKb,
                onHardwareEnter = {
                    if (composerText.trim().isNotEmpty() || attachmentState.attachments.isNotEmpty()) {
                        newAgentPrompt = composerText.trim()
                        newAgentAttachments = attachmentState.attachments.toList()
                        composerText = ""
                        composerField = TextFieldValue("")
                        attachmentState.clear()
                        onComposerOpenChange(false)
                        newAgentOpen = true
                    } else {
                        onComposerOpenChange(false)
                    }
                },
                onHardwareEscape = { onComposerOpenChange(false) },
            )
        }
    }
    SearchSheet(
        open = searchOpen,
        onDismiss = { onSearchOpenChange(false) },
        agents = agents,
        workspaces = workspaces,
        hostNames = hostRows.associate { it.id to it.name },
        onOpenAgent = { a ->
            onSearchOpenChange(false)
            onNavigate(Screen.Chat(a.hostId, a.id))
        },
        onOpenWorkspace = { ws ->
            onSearchOpenChange(false)
            onNavigate(Screen.Workspace(ws.hostId, ws.path))
        },
    )
    HomeComputersSheet(
        computersOpen,
        {
            onComputersOpenChange(false)
            onComputersDismiss()
        },
        hostRows,
        hosts,
        onPair = {
            onComputersOpenChange(false)
            onComputersDismiss()
            onNavigate(Screen.Pairing())
        },
    )
    AgentKindPickerSheet(
        open = agentPickerOpen,
        onDismiss = { agentPickerOpen = false },
        installed = installedKinds,
        current = draftKind,
        onPick = { picked ->
            draftKind = picked
            agentPickerOpen = false
        },
    )
    if (defaultHostId.isNotEmpty()) {
        ModelPickerSheets(
            open = modelOpen,
            onDismiss = { modelOpen = false },
            hosts = hosts,
            db = db,
            hostId = defaultHostId,
            kind = draftKind,
            agentId = null,
            currentModel = draftModel,
            currentEffort = draftEffort,
            onDraftChange = { model, effort ->
                if (model != null) draftModel = model
                if (effort != null) draftEffort = effort
            },
        )
    }
    PermissionModeMenu(
        open = modeMenuOpen,
        onDismiss = { modeMenuOpen = false },
        anchorBounds = modeAnchor,
        modes = modeOptions,
        current = draftMode,
        onPick = { picked ->
            draftMode = picked.lowercase()
            modeLabel = modeMenuLabel(draftMode)
        },
    )
    NewAgentConfigureSheet(
        open = newAgentOpen,
        onDismiss = { newAgentOpen = false },
        prompt = newAgentPrompt,
        hosts = hosts,
        db = db,
        targets = agentTargets,
        initialTarget = agentTargets.firstOrNull(),
        attachments = newAgentAttachments,
        vault = app.vault,
        initialModel = draftModel,
        initialEffort = draftEffort,
        initialMode = draftMode,
        initialKind = draftKind,
        chatOutbound = app.chatOutbound,
        onStarted = { h, a ->
            newAgentPrompt = ""
            newAgentAttachments = emptyList()
            newAgentOpen = false
            onNavigate(Screen.Chat(h, a))
        },
    )
    ComposerPlusMenu(
        open = plusMenuOpen,
        onDismiss = { plusMenuOpen = false },
        anchor = plusAnchor,
        onPhoto = attachmentPickers.openPhoto,
        onCamera = attachmentPickers.openCamera,
    )
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
