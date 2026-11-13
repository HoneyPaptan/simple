package sh.simple.android.link

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import sh.simple.android.model.Agent
import sh.simple.android.model.AgentCommandsResult
import sh.simple.android.model.AgentKindsResult
import sh.simple.android.model.AgentScreenResult
import sh.simple.android.model.AgentStartResult
import sh.simple.android.model.FilesListResult
import sh.simple.android.model.FilesSearchResult
import sh.simple.android.model.FilesReadResult
import sh.simple.android.model.FilesUploadResult
import sh.simple.android.model.FontsListResult
import sh.simple.android.model.GitCommitResult
import sh.simple.android.model.GitDiffResult
import sh.simple.android.model.GitStatusResult
import sh.simple.android.model.TranscriptResult
import sh.simple.android.model.PreviewOpenResult
import sh.simple.android.model.ServersListResult
import sh.simple.android.model.UsageSummaryResult
import sh.simple.android.model.WebhooksListResult
import sh.simple.android.model.WebhooksSetResult
import sh.simple.android.model.WireJson

object RemoteGateway {
    suspend fun installedKinds(hosts: Hosts, hostId: String): AgentKindsResult {
        val raw = hosts.rpc(hostId, "agent.kinds", buildJsonObject {})
        return WireJson.decodeFromJsonElement(AgentKindsResult.serializer(), raw)
    }

    suspend fun send(hosts: Hosts, hostId: String, agentId: String, text: String, cid: String? = null) {
        hosts.rpc(
            hostId,
            "agent.send",
            buildJsonObject {
                put("id", agentId)
                put("text", text)
                if (cid != null) put("cid", cid)
            },
        )
    }

    suspend fun interrupt(hosts: Hosts, hostId: String, agentId: String) {
        hosts.rpc(hostId, "agent.interrupt", buildJsonObject { put("id", agentId) })
    }

    suspend fun kill(hosts: Hosts, hostId: String, agentId: String) {
        require(agentId.isNotBlank()) { "bad_request" }
        hosts.rpc(hostId, "agent.kill", buildJsonObject { put("id", agentId) })
    }

    suspend fun remove(hosts: Hosts, hostId: String, agentId: String) {
        hosts.rpc(hostId, "agent.remove", buildJsonObject { put("id", agentId) })
    }

    suspend fun startAgent(
        hosts: Hosts,
        hostId: String,
        kind: String,
        cwd: String,
        mode: String,
        prompt: String?,
        model: String?,
        effort: String?,
        worktreeBranch: String?,
        runtime: String? = null,
    ): Agent {
        val raw = hosts.rpc(
            hostId,
            "agent.start",
            buildJsonObject {
                put("kind", kind)
                if (runtime != null) put("runtime", runtime)
                put("cwd", cwd)
                put("mode", mode)
                if (!prompt.isNullOrBlank()) put("prompt", prompt)
                if (model != null) put("model", model)
                if (effort != null) put("effort", effort)
                if (!worktreeBranch.isNullOrBlank()) {
                    put("worktree", buildJsonObject { put("branch", worktreeBranch) })
                }
            },
        )
        return WireJson.decodeFromJsonElement(AgentStartResult.serializer(), raw).agent
    }

    suspend fun agentCommands(hosts: Hosts, hostId: String, agentId: String): AgentCommandsResult {
        val raw = hosts.rpc(hostId, "agent.commands", buildJsonObject { put("id", agentId) })
        return WireJson.decodeFromJsonElement(AgentCommandsResult.serializer(), raw)
    }

    suspend fun filesSearch(hosts: Hosts, hostId: String, cwd: String, query: String): FilesSearchResult {
        val raw = hosts.rpc(
            hostId,
            "files.search",
            buildJsonObject {
                put("cwd", cwd)
                put("query", query)
            },
        )
        return WireJson.decodeFromJsonElement(FilesSearchResult.serializer(), raw)
    }

    suspend fun filesList(hosts: Hosts, hostId: String, path: String): FilesListResult {
        val raw = hosts.rpc(hostId, "files.list", buildJsonObject { put("path", path) })
        return WireJson.decodeFromJsonElement(FilesListResult.serializer(), raw)
    }

    suspend fun filesRead(hosts: Hosts, hostId: String, path: String): FilesReadResult {
        val raw = hosts.rpc(hostId, "files.read", buildJsonObject { put("path", path) })
        return WireJson.decodeFromJsonElement(FilesReadResult.serializer(), raw)
    }

    suspend fun filesMkdir(hosts: Hosts, hostId: String, path: String, name: String) {
        hosts.rpc(
            hostId,
            "files.mkdir",
            buildJsonObject {
                put("path", path)
                put("name", name)
            },
        )
    }

    suspend fun filesWrite(hosts: Hosts, hostId: String, path: String, text: String) {
        hosts.rpc(
            hostId,
            "files.write",
            buildJsonObject {
                put("path", path)
                put("text", text)
            },
        )
    }

    suspend fun filesUpload(hosts: Hosts, hostId: String, workspace: String, ext: String): String {
        val raw = hosts.rpc(
            hostId,
            "files.upload",
            buildJsonObject {
                put("workspace", workspace)
                put("ext", ext)
            },
        )
        return WireJson.decodeFromJsonElement(FilesUploadResult.serializer(), raw).path
    }

    suspend fun uploadAttachment(
        hosts: Hosts,
        vault: sh.simple.android.store.TokenVault,
        host: sh.simple.android.store.HostEntity,
        workspace: String,
        bytes: ByteArray,
        contentType: String,
        ext: String,
    ): String {
        val path = filesUpload(hosts, host.id, workspace, ext)
        HostBulk.putFile(host, vault, path, bytes, contentType)
        return path
    }

    fun promptWithAttachmentPaths(text: String, paths: List<String>): String {
        if (paths.isEmpty()) return text
        val block = paths.joinToString("\n")
        val trimmed = text.trim()
        return if (trimmed.isEmpty()) block else "$trimmed\n\n$block"
    }

    suspend fun gitStatus(hosts: Hosts, hostId: String, cwd: String): GitStatusResult {
        val raw = hosts.rpc(hostId, "git.status", buildJsonObject { put("cwd", cwd) })
        return WireJson.decodeFromJsonElement(GitStatusResult.serializer(), raw)
    }

    suspend fun usageSummary(hosts: Hosts, hostId: String, days: Int = 7): UsageSummaryResult {
        val raw = hosts.rpc(
            hostId,
            "usage.summary",
            buildJsonObject { put("days", days) },
        )
        return WireJson.decodeFromJsonElement(UsageSummaryResult.serializer(), raw)
    }

    suspend fun serversList(hosts: Hosts, hostId: String): ServersListResult {
        val raw = hosts.rpc(hostId, "servers.list", buildJsonObject {})
        return WireJson.decodeFromJsonElement(ServersListResult.serializer(), raw)
    }

    suspend fun serversKill(hosts: Hosts, hostId: String, pid: Int) {
        hosts.rpc(hostId, "servers.kill", buildJsonObject { put("pid", pid) })
    }

    suspend fun previewOpen(hosts: Hosts, hostId: String, port: Int): PreviewOpenResult {
        val raw = hosts.rpc(hostId, "preview.open", buildJsonObject { put("port", port) })
        return WireJson.decodeFromJsonElement(PreviewOpenResult.serializer(), raw)
    }

    suspend fun webhooksList(hosts: Hosts, hostId: String): WebhooksListResult {
        val raw = hosts.rpc(hostId, "webhooks.list", buildJsonObject {})
        return WireJson.decodeFromJsonElement(WebhooksListResult.serializer(), raw)
    }

    suspend fun webhooksSet(
        hosts: Hosts,
        hostId: String,
        url: String,
        kinds: List<String>,
    ): WebhooksSetResult {
        val raw = hosts.rpc(
            hostId,
            "webhooks.set",
            buildJsonObject {
                put("url", url)
                put("kinds", buildJsonArray { kinds.forEach { add(JsonPrimitive(it)) } })
            },
        )
        return WireJson.decodeFromJsonElement(WebhooksSetResult.serializer(), raw)
    }

    suspend fun webhooksRemove(hosts: Hosts, hostId: String, id: String) {
        hosts.rpc(hostId, "webhooks.remove", buildJsonObject { put("id", id) })
    }

    suspend fun webhooksTest(hosts: Hosts, hostId: String, id: String) {
        hosts.rpc(hostId, "webhooks.test", buildJsonObject { put("id", id) })
    }

    suspend fun fontsList(hosts: Hosts, hostId: String): FontsListResult {
        val raw = hosts.rpc(hostId, "fonts.list", buildJsonObject {})
        return WireJson.decodeFromJsonElement(FontsListResult.serializer(), raw)
    }

    suspend fun gitDiff(hosts: Hosts, hostId: String, cwd: String, path: String?): GitDiffResult {
        val raw = hosts.rpc(
            hostId,
            "git.diff",
            buildJsonObject {
                put("cwd", cwd)
                if (path != null) put("path", path)
            },
        )
        return WireJson.decodeFromJsonElement(GitDiffResult.serializer(), raw)
    }

    suspend fun gitCommit(
        hosts: Hosts,
        hostId: String,
        cwd: String,
        message: String,
        push: Boolean,
    ): GitCommitResult {
        val raw = hosts.rpc(
            hostId,
            "git.commit",
            buildJsonObject {
                put("cwd", cwd)
                put("message", message)
                put("push", push)
            },
        )
        return WireJson.decodeFromJsonElement(GitCommitResult.serializer(), raw)
    }

    suspend fun keys(hosts: Hosts, hostId: String, agentId: String, keys: List<String>) {
        hosts.rpc(
            hostId,
            "agent.keys",
            buildJsonObject {
                put("id", agentId)
                put("keys", buildJsonArray { keys.forEach { add(JsonPrimitive(it)) } })
            },
        )
    }

    suspend fun watch(hosts: Hosts, hostId: String, agentId: String, screen: Boolean) {
        hosts.rpc(
            hostId,
            "agent.watch",
            buildJsonObject {
                put("id", agentId)
                put("screen", screen)
            },
        )
    }

    suspend fun updateAgent(
        hosts: Hosts,
        hostId: String,
        agentId: String,
        title: String? = null,
        pinned: Boolean? = null,
        unread: Boolean? = null,
    ) {
        hosts.rpc(
            hostId,
            "agent.update",
            buildJsonObject {
                put("id", agentId)
                if (title != null) put("title", title)
                if (pinned != null) put("pinned", pinned)
                if (unread != null) put("unread", unread)
            },
        )
    }

    suspend fun transcript(
        hosts: Hosts,
        hostId: String,
        agentId: String,
        before: String? = null,
        limit: Int = 40,
    ): TranscriptResult {
        val raw = hosts.rpc(
            hostId,
            "agent.transcript",
            buildJsonObject {
                put("id", agentId)
                put("limit", limit)
                if (before != null) put("before", before)
            },
        )
        return WireJson.decodeFromJsonElement(TranscriptResult.serializer(), raw)
    }

    suspend fun screen(hosts: Hosts, hostId: String, agentId: String): AgentScreenResult {
        val raw = hosts.rpc(
            hostId,
            "agent.screen",
            buildJsonObject { put("id", agentId) },
        )
        return WireJson.decodeFromJsonElement(AgentScreenResult.serializer(), raw)
    }

    suspend fun configure(
        hosts: Hosts,
        hostId: String,
        agentId: String,
        model: String? = null,
        effort: String? = null,
        mode: String? = null,
    ) {
        hosts.rpc(
            hostId,
            "agent.configure",
            buildJsonObject {
                put("id", agentId)
                if (model != null) put("model", model)
                if (effort != null) put("effort", effort)
                if (mode != null) put("mode", mode)
            },
        )
    }

    suspend fun inboxAnswer(
        hosts: Hosts,
        hostId: String,
        itemId: String,
        decision: String,
        option: String? = null,
        text: String? = null,
    ) {
        hosts.rpc(
            hostId,
            "inbox.answer",
            buildJsonObject {
                put("id", itemId)
                put("decision", decision)
                if (option != null) put("option", option)
                if (text != null) put("text", text)
            },
        )
    }
}
