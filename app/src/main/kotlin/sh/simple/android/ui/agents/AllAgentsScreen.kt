package sh.simple.android.ui.agents

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import sh.simple.android.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sh.simple.android.link.Hosts
import sh.simple.android.store.Models
import sh.simple.android.store.SimpleDatabase
import sh.simple.android.store.rememberAgents
import sh.simple.android.store.rememberHostsForDisplay
import sh.simple.android.store.rememberWorkspaces
import sh.simple.android.store.modelDisplayLabel
import sh.simple.android.ui.composer.NewAgentConfigureSheet
import sh.simple.android.ui.composer.agentBelongsToWorkspace
import sh.simple.android.ui.composer.targetsFromWorkspaces
import sh.simple.android.ui.composer.workspaceFolderLabel
import sh.simple.android.ui.glass.GlassStage
import sh.simple.android.ui.kit.AgentRow
import sh.simple.android.ui.kit.EmptyState
import sh.simple.android.ui.kit.ComposerCard
import sh.simple.android.ui.kit.ComposerOverlay
import sh.simple.android.ui.kit.ComposerPill
import sh.simple.android.ui.kit.composerChromeInset
import sh.simple.android.ui.kit.composerListContentPadding
import sh.simple.android.ui.kit.GlassChip
import sh.simple.android.ui.kit.HeaderBar
import sh.simple.android.ui.kit.Icons
import sh.simple.android.ui.kit.LargeTitle
import sh.simple.android.ui.kit.SectionLabel
import sh.simple.android.ui.kit.rememberHideKeyboard
import sh.simple.android.ui.model.ModelPickerSheets
import sh.simple.android.ui.model.PermissionModeMenu
import sh.simple.android.ui.model.modeMenuLabel
import sh.simple.android.ui.nav.Screen
import sh.simple.android.ui.theme.Tokens
import sh.simple.android.model.Agent
import sh.simple.android.model.WireJson
import sh.simple.android.ui.util.agentKindLabel
import sh.simple.android.ui.util.agentEntityStatusLine
import sh.simple.android.ui.util.agentWire
import sh.simple.android.ui.util.agentsByRuntimeSections
import sh.simple.android.ui.util.allAgentsContextSuffix
import sh.simple.android.ui.util.allAgentsRecents
import sh.simple.android.ui.util.allAgentsWaiting
import sh.simple.android.ui.util.joinMetaParts
import sh.simple.android.ui.util.optionalComputerName
import sh.simple.android.ui.util.showPairedComputerNames

@Composable
fun AllAgentsScreen(
    db: SimpleDatabase,
    hosts: Hosts,
    hostNames: Map<String, String>,
    onBack: () -> Unit,
    onOpenChat: (String, String) -> Unit,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier,
) {
    val agents by db.rememberAgents().collectAsState(initial = emptyList())
    val workspaces by db.rememberWorkspaces().collectAsState(initial = emptyList())
    val hostRows by db.rememberHostsForDisplay().collectAsState(initial = emptyList())
    val waiting = allAgentsWaiting(agents)
    val recents = allAgentsRecents(agents)
    var composerOpen by remember { mutableStateOf(false) }
    BackHandler(composerOpen) { composerOpen = false }
    var composerText by remember { mutableStateOf("") }
    var newAgentOpen by remember { mutableStateOf(false) }
    var agentMenuOpen by remember { mutableStateOf(false) }
    var agentMenuAnchor by remember { mutableStateOf(Rect.Zero) }
    var menuAgent by remember { mutableStateOf<sh.simple.android.store.AgentEntity?>(null) }
    var renameOpen by remember { mutableStateOf(false) }
    var killOpen by remember { mutableStateOf(false) }
    var removeOpen by remember { mutableStateOf(false) }
    val defaultHostId = hostRows.firstOrNull()?.id.orEmpty()
    val showComputer = showPairedComputerNames(hostRows.size)
    val targets = targetsFromWorkspaces(hostNames, workspaces, defaultHostId, showComputer)
    val hideKeyboard = rememberHideKeyboard()
    LaunchedEffect(newAgentOpen) {
        if (newAgentOpen) hideKeyboard()
    }
    Box(modifier.fillMaxSize()) {
        GlassStage(
            modifier.fillMaxSize().background(Tokens.current.background),
            chrome = {
                HeaderBar(listOf(Icons.Back), "All Agents", emptyList(), onLeftClick = { _ -> onBack() })
                if (!composerOpen) {
                    ComposerPill(
                        "Plan, ask, build...",
                        onPlus = { composerOpen = true },
                        onMic = {},
                        onTap = { composerOpen = true },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .composerChromeInset(),
                    )
                }
            },
        ) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = composerListContentPadding(),
            ) {
                if (waiting.isNotEmpty()) {
                    item { SectionLabel("Needs you") }
                    agentsByRuntimeSections(waiting).forEach { (section, group) ->
                        if (section != null) {
                            item("waitHdr:$section") { SectionLabel(section) }
                        }
                        items(group, key = { "${it.hostId}:${it.id}" }) { agent ->
                            AgentRow(
                                agent.kind,
                                agent.title,
                                agentEntityStatusLine(agent),
                                metaSuffix(agent, hostNames, showComputer),
                                onClick = { onOpenChat(agent.hostId, agent.id) },
                                onLongClick = {
                                    menuAgent = agent
                                    agentMenuOpen = true
                                },
                                modifier = Modifier.onGloballyPositioned { agentMenuAnchor = it.boundsInRoot() },
                            )
                        }
                    }
                }
                if (waiting.isEmpty() && recents.isEmpty()) {
                    item {
                        EmptyState(
                            Icons.Folders,
                            "No agents here yet",
                            "Start one from the composer below.",
                        )
                    }
                } else {
                    val recentSections = agentsByRuntimeSections(recents.take(20))
                    val runtimeHeaders = recentSections.any { it.first != null }
                    if (!runtimeHeaders) {
                        item { SectionLabel("Recents") }
                    }
                    recentSections.forEach { (section, group) ->
                        if (section != null) {
                            item("recHdr:$section") { SectionLabel(section) }
                        }
                        items(group, key = { "${it.hostId}:${it.id}" }) { agent ->
                            AgentRow(
                                agent.kind,
                                agent.title,
                                agentEntityStatusLine(agent),
                                metaSuffix(agent, hostNames, showComputer),
                                onClick = { onOpenChat(agent.hostId, agent.id) },
                                onLongClick = {
                                    menuAgent = agent
                                    agentMenuOpen = true
                                },
                                modifier = Modifier.onGloballyPositioned { agentMenuAnchor = it.boundsInRoot() },
                            )
                        }
                    }
                }
            }
        }
        if (composerOpen) {
            ComposerOverlay(onDismiss = { composerOpen = false }) {
                ComposerCard(
                    chips = {},
                    text = composerText,
                    onTextChange = { composerText = it },
                    modeLabel = "Ask",
                    modelLabel = "Model",
                    sending = false,
                    onPlus = {},
                    onMic = {},
                    onSend = {
                        if (composerText.trim().isNotEmpty()) newAgentOpen = true
                        else composerOpen = false
                    },
                    onStop = { composerOpen = false },
                )
            }
        }
    }
    NewAgentConfigureSheet(
        open = newAgentOpen,
        onDismiss = { newAgentOpen = false },
        prompt = composerText.trim(),
        hosts = hosts,
        db = db,
        targets = targets,
        initialTarget = targets.firstOrNull(),
        vault = (LocalContext.current.applicationContext as App).vault,
        chatOutbound = (LocalContext.current.applicationContext as App).chatOutbound,
        onStarted = { h, a ->
            composerOpen = false
            composerText = ""
            newAgentOpen = false
            onOpenChat(h, a)
        },
    )
    AgentActionsHost(
        hosts = hosts,
        hostId = menuAgent?.hostId.orEmpty(),
        agent = menuAgent,
        menuOpen = agentMenuOpen,
        onMenuDismiss = { agentMenuOpen = false },
        anchor = agentMenuAnchor,
        renameOpen = renameOpen,
        onRenameOpenChange = { renameOpen = it },
        killOpen = killOpen,
        onKillOpenChange = { killOpen = it },
        removeOpen = removeOpen,
        onRemoveOpenChange = { removeOpen = it },
    )
}

private fun workspaceMetaSuffix(
    agent: sh.simple.android.store.AgentEntity,
    hostNames: Map<String, String>,
    showComputer: Boolean,
): String = metaSuffix(agent, hostNames, showComputer)

private fun metaSuffix(
    agent: sh.simple.android.store.AgentEntity,
    hostNames: Map<String, String>,
    showComputer: Boolean,
): String {
    val wire = agentWire(agent)
    val meter = allAgentsContextSuffix(wire?.context)
    val kind = agentKindLabel(agent.kind).takeUnless { it.equals(agent.title.trim(), ignoreCase = true) }
    val host = optionalComputerName(agent.hostId, hostNames, showComputer)
    return joinMetaParts(listOf(kind, meter, host))
}

@Composable
fun WorkspaceAgentsScreen(
    db: SimpleDatabase,
    hosts: Hosts,
    hostId: String,
    path: String,
    hostNames: Map<String, String>,
    onBack: () -> Unit,
    onOpenChat: (String, String) -> Unit,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier,
) {
    val agents by db.agents().observeForHost(hostId).collectAsState(initial = emptyList())
    val workspaces by db.rememberWorkspaces().collectAsState(initial = emptyList())
    val filtered = agents.filter { agentBelongsToWorkspace(it, path) }
    val headerTitle = workspaceFolderLabel(path)
    var composerOpen by remember { mutableStateOf(false) }
    BackHandler(composerOpen) { composerOpen = false }
    var composerText by remember { mutableStateOf("") }
    var newAgentOpen by remember { mutableStateOf(false) }
    var agentMenuOpen by remember { mutableStateOf(false) }
    var agentMenuAnchor by remember { mutableStateOf(Rect.Zero) }
    var menuAgent by remember { mutableStateOf<sh.simple.android.store.AgentEntity?>(null) }
    var renameOpen by remember { mutableStateOf(false) }
    var killOpen by remember { mutableStateOf(false) }
    var removeOpen by remember { mutableStateOf(false) }
    var modelOpen by remember { mutableStateOf(false) }
    var draftModel by remember { mutableStateOf<String?>("opus") }
    var draftEffort by remember { mutableStateOf("high") }
    var draftMode by remember { mutableStateOf("ask") }
    var modelLabel by remember { mutableStateOf("Model") }
    var modeLabel by remember { mutableStateOf("Ask") }
    var modeMenuOpen by remember { mutableStateOf(false) }
    var modeAnchor by remember { mutableStateOf(Rect.Zero) }
    var modeOptions by remember { mutableStateOf(listOf("ask", "plan", "auto", "full")) }
    val showComputer = showPairedComputerNames(hostNames.size)
    val targets = targetsFromWorkspaces(hostNames, workspaces, hostId, showComputer)
    val initial = targets.firstOrNull { it.hostId == hostId && it.cwd == path }
        ?: sh.simple.android.ui.composer.NewAgentTarget(hostId, path, path.substringAfterLast('/'))
    val folderName = headerTitle
    val hideKeyboard = rememberHideKeyboard()
    LaunchedEffect(newAgentOpen, modelOpen, modeMenuOpen) {
        if (newAgentOpen || modelOpen || modeMenuOpen) hideKeyboard()
    }
    LaunchedEffect(hostId, draftModel) {
        if (hostId.isEmpty()) return@LaunchedEffect
        modelLabel = withContext(Dispatchers.IO) {
            runCatching {
                val catalog = Models.load(hosts, db, hostId, "claude")
                if (catalog.modes.isNotEmpty()) modeOptions = catalog.modes
                modelDisplayLabel(catalog, draftModel)
            }.getOrDefault("Model")
        }
    }
    Box(modifier.fillMaxSize()) {
        GlassStage(
            modifier.fillMaxSize().background(Tokens.current.background),
            chrome = {
                HeaderBar(
                    listOf(Icons.Back),
                    null,
                    listOf(Icons.Grid, Icons.Folder, Icons.Branch, Icons.Server),
                    onLeftClick = { _ -> onBack() },
                    onRightClick = { index ->
                        when (index) {
                            0 -> onNavigate(Screen.Panes(hostId, path))
                            1 -> onNavigate(Screen.Files(hostId, path))
                            2 -> onNavigate(Screen.Changes(hostId, path))
                            3 -> onNavigate(Screen.Servers(hostId))
                        }
                    },
                )
                if (!composerOpen) {
                    ComposerPill(
                        "Plan, ask, build...",
                        onPlus = { composerOpen = true },
                        onMic = {},
                        onTap = { composerOpen = true },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .composerChromeInset(),
                    )
                }
            },
        ) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = composerListContentPadding(),
            ) {
                item("wsTitle") { LargeTitle(headerTitle) }
                if (filtered.isEmpty()) {
                    item {
                        EmptyState(
                            Icons.Folder,
                            "No agents here yet",
                            "Start one from the composer below.",
                        )
                    }
                }
                agentsByRuntimeSections(filtered).forEach { (section, group) ->
                    if (section != null) {
                        item("wsHdr:$section") { SectionLabel(section) }
                    }
                    items(group, key = { it.id }) { agent ->
                    AgentRow(
                        agent.kind,
                        agent.title,
                        agentEntityStatusLine(agent),
                        workspaceMetaSuffix(agent, hostNames, showComputer),
                        onClick = { onOpenChat(agent.hostId, agent.id) },
                        onLongClick = {
                            menuAgent = agent
                            agentMenuOpen = true
                        },
                        modifier = Modifier.onGloballyPositioned { agentMenuAnchor = it.boundsInRoot() },
                    )
                    }
                }
            }
        }
        if (composerOpen) {
            ComposerOverlay(onDismiss = { composerOpen = false }) {
                ComposerCard(
                    chips = {
                        GlassChip(folderName, leadingIcon = Icons.Folder)
                    },
                    text = composerText,
                    onTextChange = { composerText = it },
                    modeLabel = modeLabel,
                    modelLabel = modelLabel,
                    sending = false,
                    onPlus = {},
                    onMic = {},
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
                    onModelClick = { if (hostId.isNotEmpty()) modelOpen = true },
                    onSend = {
                        if (composerText.trim().isNotEmpty()) newAgentOpen = true
                        else composerOpen = false
                    },
                    onStop = { composerOpen = false },
                )
            }
        }
    }
    NewAgentConfigureSheet(
        open = newAgentOpen,
        onDismiss = { newAgentOpen = false },
        prompt = composerText.trim(),
        hosts = hosts,
        db = db,
        targets = targets,
        initialTarget = initial,
        vault = (LocalContext.current.applicationContext as App).vault,
        chatOutbound = (LocalContext.current.applicationContext as App).chatOutbound,
        onStarted = { h, a ->
            composerOpen = false
            composerText = ""
            newAgentOpen = false
            onOpenChat(h, a)
        },
    )
    if (hostId.isNotEmpty()) {
        ModelPickerSheets(
            open = modelOpen,
            onDismiss = { modelOpen = false },
            hosts = hosts,
            db = db,
            hostId = hostId,
            kind = "claude",
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
    AgentActionsHost(
        hosts = hosts,
        hostId = menuAgent?.hostId.orEmpty(),
        agent = menuAgent,
        menuOpen = agentMenuOpen,
        onMenuDismiss = { agentMenuOpen = false },
        anchor = agentMenuAnchor,
        renameOpen = renameOpen,
        onRenameOpenChange = { renameOpen = it },
        killOpen = killOpen,
        onKillOpenChange = { killOpen = it },
        removeOpen = removeOpen,
        onRemoveOpenChange = { removeOpen = it },
    )
}
