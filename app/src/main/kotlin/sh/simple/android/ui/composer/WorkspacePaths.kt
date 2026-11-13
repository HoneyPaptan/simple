package sh.simple.android.ui.composer

import sh.simple.android.store.AgentEntity

fun pathHasPrefix(child: String, root: String): Boolean {
    if (root.isEmpty()) return child.isEmpty()
    if (child == root) return true
    if (!child.startsWith(root)) return false
    val next = child.getOrNull(root.length) ?: return true
    return next == '/'
}

fun agentBelongsToWorkspace(agent: AgentEntity, workspacePath: String): Boolean {
    if (agent.workspace == workspacePath) return true
    if (agent.cwd.isNotEmpty() && pathHasPrefix(agent.cwd, workspacePath)) return true
    return false
}

fun workspaceFolderLabel(path: String): String = path.substringAfterLast('/').ifEmpty { path }
