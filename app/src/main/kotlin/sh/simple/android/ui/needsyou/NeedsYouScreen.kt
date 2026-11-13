package sh.simple.android.ui.needsyou

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import sh.simple.android.App
import sh.simple.android.link.Hosts
import sh.simple.android.model.Item
import sh.simple.android.model.WireJson
import sh.simple.android.store.AgentEntity
import sh.simple.android.store.ItemEntity
import sh.simple.android.store.SimpleDatabase
import sh.simple.android.ui.glass.GlassStage
import sh.simple.android.ui.inbox.InboxAnswerAutoRetryAllHostsEffect
import sh.simple.android.ui.inbox.InboxAnswerRequest
import sh.simple.android.ui.inbox.InboxItemCard
import sh.simple.android.ui.inbox.rememberInboxAnswerUiState
import sh.simple.android.ui.inbox.needsYouKinds
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import sh.simple.android.ui.util.agentKindIcon
import sh.simple.android.ui.kit.AppText
import sh.simple.android.ui.kit.EmptyState
import sh.simple.android.ui.kit.Icons
import sh.simple.android.ui.kit.HeaderBar
import sh.simple.android.ui.kit.listScrollBottomPadding
import sh.simple.android.ui.theme.Tokens
import sh.simple.android.ui.theme.Type
import sh.simple.android.ui.util.agentKindLabel
import sh.simple.android.ui.util.joinMetaParts
import sh.simple.android.ui.util.optionalComputerName
import sh.simple.android.ui.util.showPairedComputerNames

data class OpenInboxRow(
    val hostId: String,
    val hostName: String,
    val agentKind: String,
    val agentTitle: String,
    val item: Item,
)

@Composable
fun NeedsYouScreen(
    db: SimpleDatabase,
    hosts: Hosts,
    hostNames: Map<String, String>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val agents by db.agents().observeAll().collectAsState(initial = emptyList())
    val items by db.items().observeAll().collectAsState(initial = emptyList())
    val answerUi = rememberInboxAnswerUiState()
    val sent = answerUi.sent
    val answerErrors = answerUi.errors
    val scope = rememberCoroutineScope()
    val app = LocalContext.current.applicationContext as App
    val showComputer = showPairedComputerNames(hostNames.size)
    val rows = remember(agents, items, hostNames, showComputer) {
        buildOpenRows(agents, items, hostNames, showComputer)
    }
    InboxAnswerAutoRetryAllHostsEffect(
        hosts,
        rows.map { it.hostId }.toSet(),
        answerUi,
    ) { _, itemId ->
        app.inboxNotifications.cancel(itemId)
    }
    val surface = if (rows.isEmpty()) Tokens.current.brandSurface else Tokens.current.background
    GlassStage(modifier.fillMaxSize().background(surface)) {
        Column(Modifier.fillMaxSize()) {
            HeaderBar(listOf(Icons.Back), "Needs you", emptyList(), onLeftClick = { _ -> onBack() })
            if (rows.isEmpty()) {
                EmptyState(Icons.Bell, "Nothing needs you", "Agents will show up here when they need a decision.")
            } else {
                LazyColumn(contentPadding = listScrollBottomPadding()) {
                    items(rows, key = { "${it.hostId}:${it.item.id}" }) { row ->
                        Row(
                            Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            sh.simple.android.ui.kit.SimpleIcon(
                                agentKindIcon(row.agentKind),
                                size = 24.dp,
                                modifier = Modifier.padding(end = 10.dp),
                            )
                            AppText(
                                joinMetaParts(
                                    listOf(
                                        optionalComputerName(row.hostId, hostNames, showComputer),
                                        row.agentTitle,
                                    ),
                                ),
                                style = Type.rowLabel.copy(
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                ),
                                color = Tokens.current.foreground,
                            )
                        }
                        InboxItemCard(
                            row.item,
                            sent[row.item.id] == true,
                            Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
                            error = answerErrors[row.item.id].orEmpty(),
                        ) { decision, option, text ->
                            scope.launch {
                                answerUi.submit(
                                    hosts,
                                    row.hostId,
                                    row.item.id,
                                    InboxAnswerRequest(decision, option, text),
                                    onSuccess = { app.inboxNotifications.cancel(row.item.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun buildOpenRows(
    agents: List<AgentEntity>,
    items: List<ItemEntity>,
    hostNames: Map<String, String>,
    showComputer: Boolean,
): List<OpenInboxRow> {
    val titles = agents.associate { "${it.hostId}:${it.id}" to it.title }
    val kinds = agents.associate { "${it.hostId}:${it.id}" to it.kind }
    return items.mapNotNull { row ->
        val item = runCatching { WireJson.decodeFromString<Item>(row.json) }.getOrNull() ?: return@mapNotNull null
        if (item.resolved || item.kind !in needsYouKinds) return@mapNotNull null
        val key = "${row.hostId}:${item.agent}"
        OpenInboxRow(
            hostId = row.hostId,
            hostName = hostNames[row.hostId] ?: row.hostId,
            agentKind = kinds[key].orEmpty(),
            agentTitle = titles[key] ?: item.agent,
            item = item,
        )
    }
}
