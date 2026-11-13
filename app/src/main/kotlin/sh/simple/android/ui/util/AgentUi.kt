package sh.simple.android.ui.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import sh.simple.android.model.Agent
import sh.simple.android.model.AgentContext
import sh.simple.android.model.WireJson
import sh.simple.android.store.AgentEntity
import sh.simple.android.ui.kit.BrandIcons
import sh.simple.android.ui.kit.Icons
val newAgentKindOrder = listOf(
    "claude",
    "opencode",
    "cursor",
    "shell",
)

fun installedNewAgentKinds(installed: Map<String, Boolean>): List<String> =
    if (installed.isEmpty()) newAgentKindOrder else newAgentKindOrder.filter { installed[it] == true }

fun agentKindIcon(kind: String): ImageVector = when (kind) {
    "claude" -> BrandIcons.Claude
    "codex" -> BrandIcons.Codex
    "cursor" -> BrandIcons.Cursor
    "opencode" -> BrandIcons.Opencode
    "fx" -> Icons.Fx
    "shell" -> Icons.Shell
    else -> Icons.Term
}

fun agentKindLabel(kind: String): String = when (kind) {
    "claude" -> "Claude"
    "codex" -> "Codex"
    "cursor" -> "Cursor"
    "opencode" -> "OpenCode"
    "fx" -> "fx"
    "shell" -> "Shell"
    "gemini" -> "Gemini"
    "qwen" -> "Qwen"
    "copilot" -> "Copilot"
    "amp" -> "Amp"
    "goose" -> "Goose"
    "crush" -> "Crush"
    "aider" -> "Aider"
    "kimi" -> "Kimi"
    else -> kind.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}

fun agentContextLeftPercent(context: AgentContext?): Int? {
    if (context == null || context.window <= 0) return null
    val used = context.used.coerceAtLeast(0)
    val left = 100 - used * 100 / context.window
    return left.coerceAtLeast(0)
}

fun agentContextLeftLabel(context: AgentContext?): String? {
    val left = agentContextLeftPercent(context) ?: return null
    return "$left% context left"
}

fun allAgentsContextSuffix(context: AgentContext?): String? {
    val left = agentContextLeftPercent(context) ?: return null
    if (left >= 20) return null
    return agentContextLeftLabel(context)
}

fun allAgentsRecents(agents: List<AgentEntity>): List<AgentEntity> =
    agents.filter { it.status != "waiting" }.sortedByDescending { it.updated }

fun allAgentsWaiting(agents: List<AgentEntity>): List<AgentEntity> = agents.filter { it.status == "waiting" }

fun allAgentsOrder(agents: List<AgentEntity>): List<AgentEntity> = allAgentsWaiting(agents) + allAgentsRecents(agents)

fun todayHomeTileOrder(agents: List<AgentEntity>): List<AgentEntity> {
    val working = agents.filter { it.status == "working" }.sortedByDescending { it.updated }
    val waiting = agents.filter { it.status == "waiting" }.sortedByDescending { it.updated }
    val rest = agents.filter { it.status != "working" && it.status != "waiting" }.sortedByDescending { it.updated }
    return working + waiting + rest
}

fun todayHomeTileAgents(agents: List<AgentEntity>, limit: Int = 4): List<AgentEntity> =
    todayHomeTileOrder(agents).take(limit)

fun todayTilePages(agents: List<AgentEntity>, pageSize: Int = 4): List<List<AgentEntity>> {
    if (pageSize <= 0) return emptyList()
    val ordered = todayHomeTileOrder(agents)
    if (ordered.isEmpty()) return emptyList()
    return ordered.chunked(pageSize)
}

fun agentWire(agent: AgentEntity): Agent? =
    runCatching { WireJson.decodeFromString<Agent>(agent.json) }.getOrNull()

fun agentRuntime(agent: AgentEntity): String = agentWire(agent)?.runtime ?: "hc"

fun agentRuntimeLabel(runtime: String): String = if (runtime == "herdr") "herdr" else "herdr-core"

fun agentsByRuntimeSections(agents: List<AgentEntity>): List<Pair<String?, List<AgentEntity>>> {
    val runtimes = agents.map { agentRuntime(it) }.toSet()
    if (runtimes.size <= 1) return listOf(null to agents)
    val core = agents.filter { agentRuntime(it) != "herdr" }
    val herdr = agents.filter { agentRuntime(it) == "herdr" }
    return buildList {
        if (core.isNotEmpty()) add("herdr-core" to core)
        if (herdr.isNotEmpty()) add("herdr" to herdr)
    }
}

