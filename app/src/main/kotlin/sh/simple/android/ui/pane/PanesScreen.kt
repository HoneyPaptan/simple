package sh.simple.android.ui.pane

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import sh.simple.android.link.Hosts
import sh.simple.android.link.PanesGateway
import sh.simple.android.link.launchRpc
import sh.simple.android.store.SimpleDatabase
import sh.simple.android.store.rememberAgents
import sh.simple.android.model.Pane
import sh.simple.android.model.PaneTab
import sh.simple.android.model.PanesListResult
import sh.simple.android.ui.composer.workspaceFolderLabel
import sh.simple.android.ui.glass.GlassStage
import sh.simple.android.ui.kit.AppText
import sh.simple.android.ui.kit.HeaderBar
import sh.simple.android.ui.kit.EmptyState
import sh.simple.android.ui.kit.Icons
import sh.simple.android.ui.kit.LoadingRow
import sh.simple.android.ui.kit.SimpleIcon
import sh.simple.android.ui.theme.Tokens
import sh.simple.android.ui.theme.Type
import sh.simple.android.ui.util.agentKindIcon
import sh.simple.android.ui.kit.AgentStatusText
import sh.simple.android.ui.util.agentStatusLine

@Composable
fun PanesScreen(
    hosts: Hosts,
    db: SimpleDatabase,
    hostId: String,
    workspace: String,
    highlightAgentId: String?,
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit,
    onOpenTerminal: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Tokens.current
    val allAgents by db.rememberAgents().collectAsState(initial = emptyList())
    val hostAgents = remember(allAgents, hostId) { allAgents.filter { it.hostId == hostId } }
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var tabs by remember { mutableStateOf<List<PaneTab>>(emptyList()) }
    var tabIndex by remember { mutableIntStateOf(0) }
    val peekCache = remember { mutableStateMapOf<String, String>() }
    var peekGen by remember { mutableIntStateOf(0) }
    var peekActive by remember { mutableStateOf(true) }
    DisposableEffect(Unit) { onDispose { peekActive = false } }

    LaunchedEffect(hostId, workspace) {
        loading = true
        launchRpc {
            val res = runCatching { PanesGateway.list(hosts, hostId, workspace) }
                .getOrElse { PanesListResult() }
            tabs = res.tabs
            tabIndex = 0
            loading = false
        }
    }

    val visiblePanes = remember(tabs, tabIndex) {
        tabs.getOrNull(tabIndex)?.panes.orEmpty().ifEmpty {
            tabs.flatMap { it.panes }
        }
    }

    LaunchedEffect(hostId, visiblePanes, peekActive) {
        while (isActive && peekActive) {
            if (visiblePanes.isNotEmpty()) {
                val res = runCatching {
                    PanesGateway.peek(hosts, hostId, visiblePanes.map { it.id })
                }.getOrNull()
                if (res != null) {
                    res.peeks.forEach { peekCache[it.id] = it.text }
                    peekGen++
                }
            }
            delay(1000)
        }
    }

    GlassStage(modifier.fillMaxSize().background(colors.background)) {
        Column(Modifier.fillMaxSize()) {
            HeaderBar(
                listOf(Icons.Back),
                workspaceFolderLabel(workspace),
                listOf(Icons.Plus),
                subtitle = "Panes",
                onLeftClick = { _ -> onBack() },
                onRightClick = { _ ->
                    scope.launchRpc {
                        val id = runCatching { PanesGateway.spawn(hosts, hostId, workspace).id }
                            .getOrNull()
                        if (id != null) onOpenTerminal(id)
                    }
                },
            )
            if (loading) {
                LoadingRow(3)
            } else {
                val herdrTabs = tabs.filter { it.id.isNotEmpty() }
                if (herdrTabs.size > 1) {
                    val chipLabels = remember(herdrTabs) { herdrTabChipLabels(herdrTabs) }
                    val tabRowState = rememberLazyListState()
                    LaunchedEffect(tabIndex) {
                        if (tabIndex in herdrTabs.indices) tabRowState.animateScrollToItem(tabIndex)
                    }
                    LazyRow(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        state = tabRowState,
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(herdrTabs, key = { _, tab -> tab.id }) { index, tab ->
                            val picked = index == tabIndex
                            AppText(
                                chipLabels.getOrElse(index) { tab.label.ifEmpty { "Tab" } },
                                Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (picked) colors.raised else colors.card)
                                    .border(0.5.dp, colors.hairline, RoundedCornerShape(16.dp))
                                    .clickable(role = Role.Button) { tabIndex = index }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                color = if (picked) colors.foreground else colors.muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (visiblePanes.isEmpty()) {
                    EmptyState(
                        Icons.Grid,
                        "No panes here yet",
                        "Open a terminal with the plus button above.",
                    )
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(visiblePanes, key = { it.id }) { pane ->
                        val highlighted = pane.id == highlightAgentId
                        PaneCard(
                            pane = pane,
                            preview = peekCache[pane.id].orEmpty(),
                            peekGen = peekGen,
                            highlighted = highlighted,
                            onClick = {
                                if (paneHostsAgent(pane)) {
                                    onOpenChat(resolvePaneChatAgentId(pane, hostAgents))
                                } else {
                                    onOpenTerminal(pane.id)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PaneCard(
    pane: Pane,
    preview: String,
    peekGen: Int,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    val colors = Tokens.current
    val shape = RoundedCornerShape(18.dp)
    val statusLine = agentStatusLine(pane.status, null, null, 0L).let { line ->
        if (line.shimmer) line else line.copy(text = line.text.substringBefore(" · "))
    }
    val border = if (highlighted) colors.foreground else colors.hairline
    Column(
        Modifier
            .clip(shape)
            .background(colors.card)
            .border(if (highlighted) 1.5.dp else 0.5.dp, border, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(12.dp),
    ) {
        SimpleIcon(agentKindIcon(pane.kind), size = 20.dp)
        Spacer(Modifier.height(6.dp))
        AppText(
            pane.title,
            style = Type.rowLabel,
            color = colors.foreground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        AgentStatusText(statusLine)
        Spacer(Modifier.height(8.dp))
        val previewLines = remember(preview, peekGen) { formatPanePeekPreview(preview) }
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(colors.background)
                .padding(8.dp),
        ) {
            if (previewLines.isNotEmpty()) {
                AppText(
                    previewLines,
                    style = Type.code,
                    color = colors.faint,
                    maxLines = 5,
                )
            } else {
                AppText("No output yet", style = Type.meta, color = colors.muted)
            }
        }
    }
}
