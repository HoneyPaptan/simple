package sh.simple.android.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.kyant.shapes.Capsule
import sh.simple.android.store.AgentEntity
import sh.simple.android.store.WorkspaceEntity
import sh.simple.android.ui.kit.AppText
import sh.simple.android.ui.kit.EmptyState
import sh.simple.android.ui.kit.GlassSheet
import sh.simple.android.ui.kit.Icons
import sh.simple.android.ui.kit.ListRow
import sh.simple.android.ui.kit.SectionLabel
import sh.simple.android.ui.theme.Tokens
import sh.simple.android.ui.theme.Type
import sh.simple.android.ui.util.agentKindIcon
import sh.simple.android.ui.util.agentKindLabel
import sh.simple.android.ui.kit.AgentStatusText
import sh.simple.android.ui.util.agentEntityStatusLine
import sh.simple.android.ui.util.optionalComputerName
import sh.simple.android.ui.util.showPairedComputerNames

data class SearchResults(val agents: List<AgentEntity>, val workspaces: List<WorkspaceEntity>)

fun searchHome(query: String, agents: List<AgentEntity>, workspaces: List<WorkspaceEntity>): SearchResults {
    val q = query.trim()
    if (q.isEmpty()) return SearchResults(emptyList(), emptyList())
    fun hit(vararg fields: String) = fields.any { it.contains(q, ignoreCase = true) }
    return SearchResults(
        agents.filter { hit(it.title, agentKindLabel(it.kind), it.workspace.substringAfterLast('/')) },
        workspaces.filter { hit(it.name, it.path) },
    )
}

@Composable
fun SearchSheet(
    open: Boolean,
    onDismiss: () -> Unit,
    agents: List<AgentEntity>,
    workspaces: List<WorkspaceEntity>,
    hostNames: Map<String, String>,
    onOpenAgent: (AgentEntity) -> Unit,
    onOpenWorkspace: (WorkspaceEntity) -> Unit,
) {
    var query by remember(open) { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val results = remember(query, agents, workspaces) { searchHome(query, agents, workspaces) }
    val showComputer = showPairedComputerNames(hostNames.size)
    GlassSheet(open, onDismiss, "Search") {
        Column(Modifier.fillMaxWidth()) {
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp)
                    .clip(Capsule())
                    .background(Tokens.current.raised)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .focusRequester(focus),
                textStyle = Type.body.copy(color = Tokens.current.foreground),
                cursorBrush = SolidColor(Tokens.current.foreground),
                singleLine = true,
                decorationBox = { field ->
                    if (query.isEmpty()) {
                        AppText("Agents and workspaces", style = Type.body, color = Tokens.current.muted)
                    }
                    field()
                },
            )
            LaunchedEffect(open) { if (open) runCatching { focus.requestFocus() } }
            LazyColumn(Modifier.fillMaxWidth()) {
                if (results.agents.isNotEmpty()) {
                    item { SectionLabel("Agents") }
                    items(results.agents, key = { "a:${it.hostId}:${it.id}" }) { agent ->
                        ListRow(
                            agentKindIcon(agent.kind),
                            agent.title,
                            secondaryContent = {
                                Row {
                                    AgentStatusText(agentEntityStatusLine(agent))
                                    optionalComputerName(agent.hostId, hostNames, showComputer)?.let { host ->
                                        AppText(
                                            " · $host",
                                            style = Type.meta,
                                            color = Tokens.current.muted,
                                            maxLines = 1,
                                        )
                                    }
                                }
                            },
                            onClick = { onOpenAgent(agent) },
                        )
                    }
                }
                if (results.workspaces.isNotEmpty()) {
                    item { SectionLabel("Workspaces") }
                    items(results.workspaces, key = { "w:${it.hostId}:${it.path}" }) { ws ->
                        ListRow(
                            Icons.Folder,
                            ws.name,
                            secondary = optionalComputerName(ws.hostId, hostNames, showComputer).orEmpty(),
                            onClick = { onOpenWorkspace(ws) },
                        )
                    }
                }
                if (query.isNotBlank() && results.agents.isEmpty() && results.workspaces.isEmpty()) {
                    item {
                        EmptyState(Icons.Search, "No matches for ${query.trim()}", "Try another word or check spelling.")
                    }
                }
            }
        }
    }
}
