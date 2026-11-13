package sh.simple.android.ui.util

enum class AgentStatusTone {
    Muted,
    Attention,
    Remove,
}

data class AgentStatusLine(
    val text: String,
    val tone: AgentStatusTone,
    val shimmer: Boolean,
    val workingTail: String = "",
)

fun agentWaitingLabel(waiting: String?): String {
    val raw = waiting?.trim().orEmpty()
    if (raw.isEmpty()) return "Waiting for input"
    when (raw.lowercase()) {
        "approval", "permission" -> return "Wants to run a command"
        "question" -> return "Has a question"
        "plan" -> return "Plan ready"
        "auth", "needs you" -> return "Waiting for input"
    }
    if (raw.contains("permission", ignoreCase = true)) return "Wants to run a command"
    if (raw.contains("question", ignoreCase = true)) return "Has a question"
    if (raw.contains("plan", ignoreCase = true)) return "Plan ready"
    return "Waiting for input"
}

fun formatRelativeAge(updatedMs: Long, nowMs: Long = System.currentTimeMillis()): String {
    val seconds = ((nowMs - updatedMs) / 1000).coerceAtLeast(0)
    return when {
        seconds < 60 -> "now"
        seconds < 3600 -> "${seconds / 60}m"
        seconds < 86_400 -> "${seconds / 3600}h"
        else -> "${seconds / 86_400}d"
    }
}

fun agentStatusLine(
    status: String,
    waiting: String?,
    currentTool: String?,
    updatedMs: Long,
    nowMs: Long = System.currentTimeMillis(),
): AgentStatusLine = when (status) {
    "working" -> {
        val tool = currentTool?.trim().orEmpty()
        val tail = if (tool.isNotEmpty()) " · $tool" else ""
        AgentStatusLine("Working$tail", AgentStatusTone.Muted, shimmer = true, workingTail = tail)
    }
    "waiting" -> AgentStatusLine(
        agentWaitingLabel(waiting),
        AgentStatusTone.Attention,
        shimmer = false,
    )
    "failed" -> AgentStatusLine("Stopped with an error", AgentStatusTone.Remove, shimmer = false)
    "done" -> AgentStatusLine(
        "Done · ${formatRelativeAge(updatedMs, nowMs)}",
        AgentStatusTone.Muted,
        shimmer = false,
    )
    "idle" -> AgentStatusLine(
        "Idle · ${formatRelativeAge(updatedMs, nowMs)}",
        AgentStatusTone.Muted,
        shimmer = false,
    )
    else -> AgentStatusLine(
        "Idle · ${formatRelativeAge(updatedMs, nowMs)}",
        AgentStatusTone.Muted,
        shimmer = false,
    )
}

fun agentEntityStatusLine(entity: sh.simple.android.store.AgentEntity, currentTool: String? = null): AgentStatusLine =
    agentStatusLine(entity.status, entity.waiting, currentTool, entity.updated)
