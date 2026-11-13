package sh.simple.android.ui.composer

import sh.simple.android.store.AgentEntity
import sh.simple.android.store.WorkspaceEntity

private val homePathPattern = Regex("^(/home/[^/]+|/Users/[^/]+|/root)(/.*)?$")

fun pathWithTilde(path: String): String {
    if (path == "~" || path.startsWith("~/")) return path
    val match = homePathPattern.matchEntire(path) ?: return path
    val rest = match.groupValues.getOrNull(2).orEmpty()
    return if (rest.isEmpty()) "~" else "~$rest"
}

fun recentDirectoriesForHost(
    hostId: String,
    workspaces: List<WorkspaceEntity>,
    agents: List<AgentEntity>,
    limit: Int = 8,
): List<String> {
    val scored = linkedMapOf<String, Long>()
    for (ws in workspaces) {
        if (ws.hostId != hostId || ws.path.isBlank()) continue
        scored[ws.path] = maxOf(scored[ws.path] ?: 0L, ws.updated)
    }
    for (agent in agents) {
        if (agent.hostId != hostId) continue
        val path = agent.cwd.takeIf { it.isNotEmpty() } ?: agent.workspace
        if (path.isBlank()) continue
        scored[path] = maxOf(scored[path] ?: 0L, agent.updated)
    }
    return scored.entries
        .sortedByDescending { it.value }
        .take(limit)
        .map { it.key }
}
