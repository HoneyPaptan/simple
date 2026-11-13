package sh.simple.android.ui.pane

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import sh.simple.android.store.AgentEntity
import sh.simple.android.ui.kit.ActionRow
import sh.simple.android.ui.kit.AgentStatusText
import sh.simple.android.ui.kit.AppText
import sh.simple.android.ui.kit.GlassSheet
import sh.simple.android.ui.kit.SimpleIcon
import sh.simple.android.ui.theme.Tokens
import sh.simple.android.ui.theme.Type
import sh.simple.android.ui.util.agentEntityStatusLine
import sh.simple.android.ui.util.agentKindIcon

@Composable
fun PaneMapSheet(
    open: Boolean,
    onDismiss: () -> Unit,
    agents: List<AgentEntity>,
    hostNames: Map<String, String>,
    onPick: (AgentEntity) -> Unit,
) {
    GlassSheet(open, onDismiss, "Agents", stacked = true) {
        LazyColumn(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            items(agents, key = { it.id }) { agent ->
                val host = hostNames[agent.hostId].orEmpty()
                ActionRow(
                    title = agent.title,
                    onClick = { onPick(agent) },
                    leading = { SimpleIcon(agentKindIcon(agent.kind), size = 22.dp) },
                    subtitleContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AgentStatusText(agentEntityStatusLine(agent))
                            if (host.isNotEmpty()) {
                                AppText(" · $host", style = Type.meta, color = Tokens.current.muted, maxLines = 1)
                            }
                        }
                    },
                ) {}
            }
        }
    }
}
