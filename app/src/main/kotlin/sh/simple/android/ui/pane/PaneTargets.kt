package sh.simple.android.ui.pane

import sh.simple.android.model.Pane
import sh.simple.android.store.AgentEntity

fun paneHostsAgent(pane: Pane): Boolean = pane.kind != "shell" && pane.kind != "other"

fun resolvePaneChatAgentId(pane: Pane, hostAgents: List<AgentEntity>): String {
    if (hostAgents.isEmpty()) return pane.id
    val ids = hostAgents.associateBy { it.id }
    if (pane.id in ids) return pane.id
    val barePane = pane.id.removePrefix("h:")
    hostAgents.firstOrNull { it.id.removePrefix("h:") == barePane }?.let { return it.id }
    val prefixed = if (pane.id.startsWith("h:")) pane.id else "h:$pane.id"
    if (prefixed in ids) return prefixed
    hostAgents.firstOrNull { sameHerdrPane(it.id, pane.id) }?.let { return it.id }
    return pane.id
}

private fun sameHerdrPane(agentId: String, paneId: String): Boolean {
    val a = agentId.removePrefix("h:")
    val p = paneId.removePrefix("h:")
    return a.isNotEmpty() && a == p
}
