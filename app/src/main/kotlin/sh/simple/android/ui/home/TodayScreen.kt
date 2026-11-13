package sh.simple.android.ui.home

import sh.simple.android.ui.kit.MinTouchTarget
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import sh.simple.android.link.Hosts
import sh.simple.android.model.Item
import sh.simple.android.model.WireJson
import sh.simple.android.store.AgentEntity
import sh.simple.android.store.SimpleDatabase
import sh.simple.android.store.rememberAgents
import sh.simple.android.store.rememberItems
import sh.simple.android.ui.agents.AgentActionsHost
import sh.simple.android.ui.util.todayHomeTileOrder
import sh.simple.android.ui.inbox.needsYouKinds
import sh.simple.android.ui.kit.AppText
import sh.simple.android.ui.glass.GlassStage
import sh.simple.android.ui.kit.HeaderBar
import sh.simple.android.ui.kit.headerBarListTopPadding
import sh.simple.android.ui.kit.Icons
import sh.simple.android.ui.kit.PrimaryButton
import sh.simple.android.ui.kit.SecondaryButton
import sh.simple.android.ui.kit.homeKeyboardShortcuts
import sh.simple.android.ui.kit.rememberShortcutFocus
import sh.simple.android.ui.nav.Screen
import sh.simple.android.ui.theme.Tokens
import sh.simple.android.ui.theme.Type
import sh.simple.android.voice.VoiceHistoryStore
import sh.simple.android.voice.VoiceModelStore
import sh.simple.android.voice.VoiceReplacementsStore
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TodayScreen(
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
    val agents by db.rememberAgents().collectAsState(initial = emptyList())
    val items by db.rememberItems().collectAsState(initial = emptyList())
    val tileAgents = remember(agents) { todayHomeTileOrder(agents) }
    var computersOpen by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var composerOpen by remember { mutableStateOf(false) }
    var agentMenuOpen by remember { mutableStateOf(false) }
    var agentMenuAnchor by remember { mutableStateOf(Rect.Zero) }
    var menuAgent by remember { mutableStateOf<AgentEntity?>(null) }
    var renameOpen by remember { mutableStateOf(false) }
    var killOpen by remember { mutableStateOf(false) }
    var removeOpen by remember { mutableStateOf(false) }
    val needsYou = items.mapNotNull { decodeItem(it) }.count { !it.resolved && it.kind in needsYouKinds }
  val dateLabel = remember { formatTodayDate() }
    val secondaryLabel = if (needsYou > 0) "Needs you · $needsYou" else "All agents"
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
            Modifier
                .fillMaxSize()
                .background(Tokens.current.background)
                .todayDotGrid(),
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
            },
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(top = headerBarListTopPadding().calculateTopPadding()),
            ) {
            TodayTitleRow(
                dateLabel = dateLabel,
                onWorkspaces = { onNavigate(Screen.Workspaces) },
            )
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                TodayTileCluster(
                    tileAgents,
                    db,
                    onOpenChat = { h, a -> onNavigate(Screen.Chat(h, a)) },
                    onLongPress = { agent, anchor ->
                        menuAgent = agent
                        agentMenuAnchor = anchor
                        agentMenuOpen = true
                    },
                )
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PrimaryButton("What should we build?", onClick = { composerOpen = true })
                SecondaryButton(secondaryLabel, onClick = {
                    if (needsYou > 0) onNavigate(Screen.NeedsYou) else onNavigate(Screen.AllAgents)
                })
            }
            }
        }
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

@Composable
private fun TodayTitleRow(dateLabel: String, onWorkspaces: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            AppText("Today", Modifier.semantics { heading() }, style = Type.largeTitle)
            AppText(dateLabel, Modifier.padding(top = 2.dp), style = Type.meta, color = Tokens.current.muted)
        }
        Column(
            Modifier
                .sizeIn(minWidth = MinTouchTarget, minHeight = MinTouchTarget)
                .clickable(role = Role.Button, onClick = onWorkspaces)
                .semantics(mergeDescendants = true) {
                    heading()
                    contentDescription = "Workspaces, See all"
                },
            horizontalAlignment = Alignment.End,
        ) {
            AppText("Workspaces", style = Type.rowLabel, color = Tokens.current.foreground)
            AppText("See all", Modifier.padding(top = 2.dp), style = Type.meta, color = Tokens.current.muted)
        }
    }
}

private fun formatTodayDate(now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): String {
    val formatter = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())
    return formatter.format(now.atZone(zone))
}

private fun decodeItem(row: sh.simple.android.store.ItemEntity): Item? =
    runCatching { WireJson.decodeFromString<Item>(row.json) }.getOrNull()
