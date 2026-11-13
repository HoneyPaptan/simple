package sh.simple.android.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.shapes.RoundedRectangle
import sh.simple.android.store.AgentEntity
import sh.simple.android.store.SimpleDatabase
import sh.simple.android.store.rememberMessages
import sh.simple.android.ui.composer.workspaceFolderLabel
import sh.simple.android.ui.kit.AgentKindBadge
import sh.simple.android.ui.kit.AppText
import sh.simple.android.ui.kit.DotGridLoader
import sh.simple.android.ui.kit.EmptyState
import sh.simple.android.ui.kit.Icons
import sh.simple.android.ui.theme.Tokens
import sh.simple.android.ui.theme.Type
import sh.simple.android.ui.util.agentWaitingLabel
import sh.simple.android.ui.util.formatRelativeAge
import sh.simple.android.ui.util.rememberSampledImage
import sh.simple.android.ui.util.todayTilePages

@Composable
fun TodayTileCluster(
    tiles: List<AgentEntity>,
    db: SimpleDatabase,
    onOpenChat: (String, String) -> Unit,
    onLongPress: (AgentEntity, androidx.compose.ui.geometry.Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tiles.isEmpty()) {
        EmptyState(
            Icons.Folders,
            "Nothing running yet",
            "Start an agent with the button below.",
            modifier.padding(horizontal = 28.dp),
        )
        return
    }
    val gap = 8.dp
    val pages = remember(tiles) { todayTilePages(tiles) }
    val pagerState = rememberPagerState(pageCount = { pages.size })
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 0,
            key = { page -> pages[page].joinToString { it.id } },
        ) { page ->
            val pageTiles = pages[page]
            val columns = if (pageTiles.size == 1) 1 else 2
            val rows = (pageTiles.size + columns - 1) / columns
            val byWidth = (maxWidth - gap) / 2
            val byHeight = if (constraints.hasBoundedHeight) (maxHeight - gap * (rows - 1)) / rows else byWidth
            val tileSide = minOf(byWidth, byHeight).coerceAtMost(180.dp)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    var index = 0
                    repeat(rows) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(gap),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val cells = minOf(columns, pageTiles.size - index)
                            repeat(cells) {
                                val agent = pageTiles[index++]
                                TodayAgentTile(
                                    agent,
                                    db,
                                    onOpenChat,
                                    onLongPress,
                                    Modifier.size(tileSide).aspectRatio(1f),
                                )
                            }
                            if (cells < columns) {
                                repeat(columns - cells) {
                                    Box(Modifier.size(tileSide))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TodayAgentTile(
    agent: AgentEntity,
    db: SimpleDatabase,
    onOpenChat: (String, String) -> Unit,
    onLongPress: (AgentEntity, androidx.compose.ui.geometry.Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    var anchor by remember { mutableStateOf(Rect.Zero) }
    val messages by db.rememberMessages(agent.hostId, agent.id).collectAsState(initial = emptyList())
    val storedImages by db.messageImages().observeForAgent(agent.hostId, agent.id).collectAsState(initial = emptyList())
    val preview = remember(messages) { latestAssistantPreview(messages) }
    val imagePath = remember(messages, storedImages) { tilePreviewImagePath(messages, storedImages) }
    val sampled by rememberSampledImage(imagePath.orEmpty(), 512)
    val bitmap = sampled
    val workspace = workspaceFolderLabel(agent.workspace)
    val age = formatRelativeAge(agent.updated)
    val waiting = agent.status == "waiting"
    val working = agent.status == "working"
    val shape = RoundedRectangle(20.dp)
    val headline = agent.title.ifBlank { workspace }
    val tileLabel = buildString {
        append(workspace)
        append(", ")
        append(headline)
        when {
            working -> append(", working")
            waiting -> append(", ${agentWaitingLabel(agent.waiting)}")
        }
        if (preview.isNotEmpty() && !waiting) {
            append(", ")
            append(preview)
        }
    }
    Box(
        modifier
            .shadow(10.dp, shape, spotColor = Tokens.current.glassShadow, ambientColor = Tokens.current.glassShadow)
            .clip(shape)
            .background(Tokens.current.card)
            .onGloballyPositioned { anchor = it.boundsInRoot() }
            .combinedClickable(
                role = Role.Button,
                onClick = { onOpenChat(agent.hostId, agent.id) },
                onLongClick = { onLongPress(agent, anchor) },
            )
            .semantics(mergeDescendants = true) { contentDescription = tileLabel },
    ) {
        if (imagePath != null && bitmap != null) {
                Image(
                    bitmap,
                    contentDescription = null,
                    Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.38f)),
            )
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    AgentKindBadge(agent.kind, Modifier.padding(end = 4.dp))
                    AppText(
                        workspace,
                        Modifier.weight(1f),
                        style = Type.meta,
                        color = Tokens.current.onPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    when {
                        working -> DotGridLoader(Tokens.current.onPrimary, size = 18.dp)
                        waiting -> Unit
                        else -> AppText(age, style = Type.meta, color = Tokens.current.onPrimary, maxLines = 1)
                    }
                }
                if (waiting) {
                    AppText(
                        agentWaitingLabel(agent.waiting),
                        Modifier
                            .padding(top = 8.dp)
                            .weight(1f, fill = true),
                        style = Type.body,
                        color = Tokens.current.attention,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    AgentKindBadge(agent.kind, Modifier.padding(end = 4.dp))
                    AppText(
                        workspace,
                        Modifier.weight(1f),
                        style = Type.meta,
                        color = Tokens.current.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    when {
                        working -> DotGridLoader(Tokens.current.muted, size = 18.dp)
                        waiting -> Unit
                        else -> AppText(age, style = Type.meta, color = Tokens.current.muted, maxLines = 1)
                    }
                }
                if (waiting) {
                    AppText(
                        agentWaitingLabel(agent.waiting),
                        Modifier
                            .padding(top = 8.dp)
                            .weight(1f, fill = true),
                        style = Type.body,
                        color = Tokens.current.attention,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else if (preview.isNotEmpty()) {
                    AppText(
                        preview,
                        Modifier.padding(top = 8.dp),
                        style = Type.body,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    AppText(
                        agent.title,
                        Modifier.padding(top = 8.dp),
                        style = Type.body,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
