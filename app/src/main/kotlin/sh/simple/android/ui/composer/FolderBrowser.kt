package sh.simple.android.ui.composer

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import sh.simple.android.link.Hosts
import sh.simple.android.link.LinkDroppedException
import sh.simple.android.link.RemoteGateway
import sh.simple.android.model.FileEntry
import sh.simple.android.store.AgentEntity
import sh.simple.android.store.WorkspaceEntity

object FolderListCache {
    private val entries = linkedMapOf<Pair<String, String>, List<FileEntry>>()

    fun get(hostId: String, path: String): List<FileEntry>? = entries[hostId to path]

    fun put(hostId: String, path: String, list: List<FileEntry>) {
        entries[hostId to path] = list
    }
}

data class FolderDirListing(
    val visible: List<FileEntry>,
    val hidden: List<FileEntry>,
)

fun folderDirListing(entries: List<FileEntry>): FolderDirListing {
    val dirs = entries.filter { it.dir }
    val sorted = dirs.sortedBy { it.name.lowercase() }
    val visible = sorted.filter { !it.name.startsWith('.') }
    val hidden = sorted.filter { it.name.startsWith('.') }
    return FolderDirListing(visible, hidden)
}

suspend fun remoteDirExists(hosts: Hosts, hostId: String, path: String): Boolean =
    try {
        RemoteGateway.filesList(hosts, hostId, path)
        true
    } catch (e: LinkDroppedException) {
        e.kind != "not_found"
    } catch (_: Exception) {
        true
    }

suspend fun existingPathsAmong(hosts: Hosts, hostId: String, paths: List<String>): Set<String> = coroutineScope {
    paths.distinct().map { path ->
        async {
            if (remoteDirExists(hosts, hostId, path)) path else null
        }
    }.awaitAll().filterNotNull().toSet()
}

fun filterRecentPaths(paths: List<String>, existing: Set<String>): List<String> =
    paths.filter { existing.contains(it) }

fun homeWorkspacesVisible(
    workspaces: List<WorkspaceEntity>,
    agents: List<AgentEntity>,
    existing: Set<String>,
): List<WorkspaceEntity> = workspaces.filter { ws ->
    existing.contains(ws.path) || agents.any { agent ->
        agent.hostId == ws.hostId && agentBelongsToWorkspace(agent, ws.path)
    }
}
