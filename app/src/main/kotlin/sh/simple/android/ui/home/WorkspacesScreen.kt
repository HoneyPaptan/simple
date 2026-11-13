package sh.simple.android.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sh.simple.android.link.Hosts
import sh.simple.android.link.RemoteGateway
import sh.simple.android.model.Item
import sh.simple.android.model.WireJson
import sh.simple.android.store.SimpleDatabase
import sh.simple.android.store.rememberAgents
import sh.simple.android.store.rememberHostsForDisplay
import sh.simple.android.store.rememberItems
import sh.simple.android.store.rememberWorkspaces
import sh.simple.android.ui.composer.FolderBrowserSheet
import sh.simple.android.ui.composer.FolderListCache
import sh.simple.android.ui.composer.existingPathsAmong
import sh.simple.android.ui.composer.homeWorkspacesVisible
import sh.simple.android.ui.composer.recentDirectoriesForHost
import sh.simple.android.ui.composer.workspaceFolderLabel
import sh.simple.android.ui.glass.GlassStage
import sh.simple.android.ui.inbox.needsYouKinds
import sh.simple.android.ui.kit.ComposerPill
import sh.simple.android.ui.kit.HeaderBar
import sh.simple.android.ui.kit.Icons
import sh.simple.android.ui.kit.LargeTitle
import sh.simple.android.ui.kit.ListRow
import sh.simple.android.ui.kit.SectionLabel
import sh.simple.android.ui.kit.composerChromeInset
import sh.simple.android.ui.kit.composerListContentPadding
import sh.simple.android.ui.kit.homeKeyboardShortcuts
import sh.simple.android.ui.kit.rememberShortcutFocus
import sh.simple.android.ui.nav.Screen
import sh.simple.android.ui.theme.Tokens
import sh.simple.android.ui.util.showPairedComputerNames
import sh.simple.android.voice.VoiceHistoryStore
import sh.simple.android.voice.VoiceModelStore
import sh.simple.android.voice.VoiceReplacementsStore
import androidx.compose.ui.Alignment

@Composable
fun WorkspacesScreen(
    db: SimpleDatabase,
    hosts: Hosts,
    onNavigate: (Screen) -> Unit,
    onOpenSettings: () -> Unit,
    voiceStore: VoiceModelStore,
    voiceReplacements: VoiceReplacementsStore,
    voiceHistory: VoiceHistoryStore,
    modifier: Modifier = Modifier,
    openComputers: Boolean = false,
    onComputersOpened: () -> Unit = {},
    onComputersDismiss: () -> Unit = {},
) {
    val hostRows by db.rememberHostsForDisplay().collectAsState(initial = emptyList())
    val agents by db.rememberAgents().collectAsState(initial = emptyList())
    val workspaces by db.rememberWorkspaces().collectAsState(initial = emptyList())
    val items by db.rememberItems().collectAsState(initial = emptyList())
    var computersOpen by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var composerOpen by remember { mutableStateOf(false) }
    var addWorkspaceFolderOpen by remember { mutableStateOf(false) }
    val defaultHostId = hostRows.firstOrNull()?.id.orEmpty()
    val recentFolders = remember(defaultHostId, agents, workspaces) {
        recentDirectoriesForHost(defaultHostId, workspaces, agents)
    }
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
    val needsYou = items.mapNotNull { decodeItem(it) }.count { !it.resolved && it.kind in needsYouKinds }
    val grouped = visibleWorkspaces.groupBy { it.hostId }
    Box(
        modifier
            .fillMaxSize()
            .homeKeyboardShortcuts(
                !composerOpen,
                onSearch = { searchOpen = true },
                onNewAgent = { composerOpen = true },
            )
            .then(rememberShortcutFocus(composerOpen)),
    ) {
        GlassStage(
            Modifier.fillMaxSize().background(Tokens.current.background),
            chrome = {
                HeaderBar(
                    left = listOf(Icons.Person),
                    title = null,
                    right = listOf(Icons.Search, Icons.Plus),
                    onLeftClick = { onOpenSettings() },
                    onRightClick = { index ->
                        when (index) {
                            0 -> searchOpen = true
                            1 -> computersOpen = true
                        }
                    },
                )
                if (!composerOpen) {
                    ComposerPill(
                        "Plan, ask, build...",
                        onPlus = { composerOpen = true },
                        onMic = { composerOpen = true },
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
                    item { LargeTitle("Workspaces") }
                    item {
                        ListRow(Icons.Folders, "All Agents", trailing = agents.size.toString()) {
                            onNavigate(Screen.AllAgents)
                        }
                    }
                    item {
                        ListRow(Icons.Bell, "Needs you", trailing = needsYou.toString()) {
                            onNavigate(Screen.NeedsYou)
                        }
                    }
                    for (host in hostRows) {
                        val hostWorkspaces = grouped[host.id].orEmpty()
                        if (hostWorkspaces.isNotEmpty()) {
                            if (showComputer) item { SectionLabel(host.name) }
                            for (ws in hostWorkspaces) {
                                item {
                                    ListRow(
                                        Icons.Folder,
                                        workspaceFolderLabel(ws.path),
                                        trailing = ws.agents.toString(),
                                        onClick = { onNavigate(Screen.Workspace(host.id, ws.path)) },
                                    )
                                }
                            }
                        }
                    }
                    item {
                        ListRow(Icons.FolderPlus, "Add Workspace", chevron = false, muted = true) {
                            when {
                                defaultHostId.isEmpty() -> computersOpen = true
                                else -> addWorkspaceFolderOpen = true
                            }
                        }
                    }
                }
        }
    }
    if (defaultHostId.isNotEmpty()) {
        FolderBrowserSheet(
            open = addWorkspaceFolderOpen,
            onDismiss = { addWorkspaceFolderOpen = false },
            hosts = hosts,
            hostId = defaultHostId,
            recentPaths = recentFolders,
            onPick = { path, _ ->
                addWorkspaceFolderOpen = false
                onNavigate(Screen.Workspace(defaultHostId, path))
            },
        )
    }
    HomeAgentComposerLayer(
        db = db,
        hosts = hosts,
        onNavigate = onNavigate,
        voiceStore = voiceStore,
        voiceReplacements = voiceReplacements,
        voiceHistory = voiceHistory,
        composerOpen = composerOpen,
        onComposerOpenChange = { composerOpen = it },
        searchOpen = searchOpen,
        onSearchOpenChange = { searchOpen = it },
        computersOpen = computersOpen,
        onComputersOpenChange = { computersOpen = it },
        onComputersDismiss = onComputersDismiss,
        openComputers = openComputers,
        onComputersOpened = onComputersOpened,
    )
}

private fun decodeItem(row: sh.simple.android.store.ItemEntity): Item? =
    runCatching { WireJson.decodeFromString<Item>(row.json) }.getOrNull()
