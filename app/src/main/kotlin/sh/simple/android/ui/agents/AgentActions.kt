package sh.simple.android.ui.agents

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import sh.simple.android.link.Hosts
import sh.simple.android.link.RemoteGateway
import sh.simple.android.link.rpcErrorMessage
import sh.simple.android.model.Agent
import sh.simple.android.model.WireJson
import sh.simple.android.store.AgentEntity
import sh.simple.android.ui.kit.AnchoredMenu
import sh.simple.android.ui.kit.AppText
import sh.simple.android.ui.kit.CenterDialog
import sh.simple.android.ui.kit.DialogButtonSpec
import sh.simple.android.ui.kit.Icons
import sh.simple.android.ui.kit.MenuEntry
import sh.simple.android.ui.theme.Tokens
import sh.simple.android.ui.theme.Type

@Composable
fun AgentActionsHost(
    hosts: Hosts,
    hostId: String,
    agent: AgentEntity?,
    menuOpen: Boolean,
    onMenuDismiss: () -> Unit,
    anchor: Rect,
    renameOpen: Boolean,
    onRenameOpenChange: (Boolean) -> Unit,
    killOpen: Boolean,
    onKillOpenChange: (Boolean) -> Unit,
    removeOpen: Boolean,
    onRemoveOpenChange: (Boolean) -> Unit,
    onRemoved: () -> Unit = {},
) {
    if (agent == null) return
    val scope = rememberCoroutineScope()
    var renameText by remember(agent.id) { mutableStateOf(agent.title) }
    var actionError by remember { mutableStateOf("") }
    val finished = agent.status == "done"
    val pinned = agent.pinned
    val runtime = remember(agent.json) {
        runCatching { WireJson.decodeFromString<Agent>(agent.json).runtime }.getOrDefault("hc")
    }
    val herdrAgent = runtime == "herdr"
    val liveAgent by rememberUpdatedState(agent)
    val liveHostId by rememberUpdatedState(hostId)

    fun runAction(block: suspend () -> Unit) {
        scope.launch {
            actionError = ""
            runCatching { block() }.onFailure { actionError = rpcErrorMessage(it) }
        }
    }

    if (actionError.isNotEmpty()) {
        AppText(
            actionError,
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            style = Type.meta,
            color = Tokens.current.remove,
            maxLines = 2,
        )
    }

    AnchoredMenu(
        open = menuOpen,
        onDismiss = onMenuDismiss,
        anchorBounds = anchor,
        items = listOf(
            MenuEntry("Rename", Icons.Pencil) {
                renameText = liveAgent.title
                onRenameOpenChange(true)
            },
            MenuEntry(if (pinned) "Unpin" else "Pin", Icons.Pin) {
                runAction { RemoteGateway.updateAgent(hosts, liveHostId, liveAgent.id, pinned = !pinned) }
            },
            MenuEntry("Mark unread", Icons.Eye) {
                runAction { RemoteGateway.updateAgent(hosts, liveHostId, liveAgent.id, unread = true) }
            },
            MenuEntry("Stop", Icons.Stop) {
                runAction { RemoteGateway.interrupt(hosts, liveHostId, liveAgent.id) }
            },
            MenuEntry("Kill", Icons.Trash, destructive = true, enabled = !herdrAgent) {
                onKillOpenChange(true)
            },
            MenuEntry(
                "Remove",
                Icons.Trash,
                destructive = true,
                enabled = finished && !herdrAgent,
            ) {
                if (finished) onRemoveOpenChange(true)
            },
        ),
    )
    CenterDialog(
        title = "Rename agent",
        visible = renameOpen,
        onDismiss = { onRenameOpenChange(false) },
        field = renameText,
        onFieldChange = { renameText = it },
        buttons = listOf(
            DialogButtonSpec("Cancel") { onRenameOpenChange(false) },
            DialogButtonSpec("Save") {
                runAction {
                    RemoteGateway.updateAgent(hosts, liveHostId, liveAgent.id, title = renameText)
                }
                onRenameOpenChange(false)
            },
        ),
    )
    CenterDialog(
        title = "Kill agent?",
        visible = killOpen,
        onDismiss = { onKillOpenChange(false) },
        body = "This stops the process on your computer.",
        buttons = listOf(
            DialogButtonSpec("Cancel") { onKillOpenChange(false) },
            DialogButtonSpec("Kill", destructive = true) {
                runAction { RemoteGateway.kill(hosts, liveHostId, liveAgent.id) }
                onKillOpenChange(false)
            },
        ),
    )
    CenterDialog(
        title = "Remove agent?",
        visible = removeOpen,
        onDismiss = { onRemoveOpenChange(false) },
        body = "Removes this finished agent from the list.",
        buttons = listOf(
            DialogButtonSpec("Cancel") { onRemoveOpenChange(false) },
            DialogButtonSpec("Remove", destructive = true) {
                runAction {
                    RemoteGateway.remove(hosts, liveHostId, liveAgent.id)
                    onRemoved()
                }
                onRemoveOpenChange(false)
            },
        ),
    )
}

private fun decodeAgent(json: String): Agent? =
    runCatching { WireJson.decodeFromString<Agent>(json) }.getOrNull()

fun agentFinished(agent: AgentEntity): Boolean {
    val wire = decodeAgent(agent.json)
    return agent.status == "done" || wire?.status == "done"
}
