package sh.simple.android.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class HelloParams(
    val app: String? = null,
    val epoch: String? = null,
    val since: Long? = null,
)

@Serializable
data class HelloRequest(
    val m: String,
    val p: HelloParams,
)

@Serializable
data class HostInfo(
    val id: String,
    val name: String,
    val version: String,
    val epoch: String? = null,
    val hc: String? = null,
    val herdr: String? = null,
    val linger: Boolean = false,
)

@Serializable
data class HelloResult(
    val host: HostInfo,
    val seq: Long,
    val replay: Boolean,
    val snapshot: Snapshot? = null,
)

@Serializable
data class Snapshot(
    val agents: List<Agent> = emptyList(),
    val inbox: List<Item> = emptyList(),
    val workspaces: List<Workspace> = emptyList(),
)

@Serializable
data class AgentContext(
    val used: Int,
    val window: Int,
)

@Serializable
data class Agent(
    val id: String,
    val runtime: String = "hc",
    val kind: String,
    val title: String,
    val cwd: String,
    val workspace: String,
    val status: String,
    val waiting: String? = null,
    val model: String? = null,
    val effort: String? = null,
    val mode: String? = null,
    val started_ms: Long,
    val updated_ms: Long,
    val pinned: Boolean = false,
    val unread: Boolean = false,
    val branch: String? = null,
    val session: String? = null,
    val context: AgentContext? = null,
)

@Serializable
data class Item(
    val id: String,
    val agent: String,
    val kind: String,
    val title: String,
    val body: String? = null,
    val command: String? = null,
    val tool: String? = null,
    val options: List<Option> = emptyList(),
    val detector: String? = null,
    val link: String? = null,
    val created_ms: Long,
    val read: Boolean = false,
    val resolved: Boolean = false,
)

val Item.promptStyle: String?
    get() = if (detector?.startsWith("menu:") == true) "choice" else detector

@Serializable
data class Option(
    val id: String,
    val label: String,
    val detail: String? = null,
)

@Serializable
data class Workspace(
    val path: String,
    val name: String,
    val branch: String? = null,
    val dirty: Boolean = false,
    val agents: Int = 0,
    val pinned: Boolean = false,
    val updated_ms: Long,
)

@Serializable
data class Message(
    val id: String,
    val role: String,
    val ts: Long,
    val text: String? = null,
    val tool: Tool? = null,
    val images: List<String>? = null,
)

@Serializable
data class Tool(
    val name: String,
    val summary: String,
    val input: String? = null,
    val output: String? = null,
    val status: String,
    val diff: String? = null,
)

@Serializable
data class ErrorBody(
    val kind: String,
    val msg: String? = null,
)

@Serializable
data class WsRequest(
    val id: Long,
    val m: String,
    val p: JsonElement? = null,
)

@Serializable
data class WsResult(
    val id: Long,
    val r: JsonElement,
)

@Serializable
data class WsError(
    val id: Long,
    val e: ErrorBody,
)

@Serializable
data class WsEvent(
    val ev: String,
    val seq: Long,
    val d: JsonElement,
)

@Serializable
data class AgentRemoved(
    val id: String,
)

@Serializable
data class PairRequest(
    val code: String,
    val device: PairDevice,
    @SerialName("replace_token") val replaceToken: String? = null,
)

@Serializable
data class PairDevice(
    val name: String,
    val model: String,
)

@Serializable
data class PairResponse(
    val device_id: String,
    val token: String,
    val host: HostInfo,
)

@Serializable
data class PairError(
    val kind: String,
)

@Serializable
data class TranscriptResult(
    val messages: List<Message> = emptyList(),
    val before: String? = null,
    val since: Long = 0L,
)

@Serializable
data class ScreenPayload(
    val id: String,
    val text: String,
    val rows: Int = 0,
    val cols: Int = 0,
)

@Serializable
data class AgentScreenResult(
    val text: String,
    val rows: Int = 0,
    val cols: Int = 0,
)

@Serializable
data class AgentsListResult(
    val agents: List<Agent> = emptyList(),
)

@Serializable
data class PaneRect(
    val x: Int = 0,
    val y: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
)

@Serializable
data class Pane(
    val id: String,
    val runtime: String,
    val workspace: String,
    val tab: String? = null,
    val title: String,
    val kind: String,
    val status: String,
    val rect: PaneRect? = null,
)

@Serializable
data class PaneTab(
    val id: String = "",
    val label: String = "",
    val panes: List<Pane> = emptyList(),
)

@Serializable
data class PanesListResult(
    val tabs: List<PaneTab> = emptyList(),
)

@Serializable
data class PanePeek(
    val id: String,
    val text: String,
)

@Serializable
data class PanesPeekResult(
    val peeks: List<PanePeek> = emptyList(),
)

@Serializable
data class PanesSpawnResult(
    val id: String,
)

@Serializable
data class WorkspacesListResult(
    val workspaces: List<Workspace> = emptyList(),
)

@Serializable
data class ModelInfo(
    val id: String,
    val label: String,
    val efforts: List<String> = emptyList(),
)

@Serializable
data class AgentModelsResult(
    val models: List<ModelInfo> = emptyList(),
    val modes: List<String> = emptyList(),
)

@Serializable
data class AgentCommandEntry(
    val name: String,
    val description: String,
    val hint: String? = null,
)

@Serializable
data class AgentCommandsResult(
    val commands: List<AgentCommandEntry> = emptyList(),
)

@Serializable
data class FilesSearchResult(
    val paths: List<String> = emptyList(),
)

@Serializable
data class AgentKindsResult(
    val kinds: Map<String, Boolean> = emptyMap(),
)

@Serializable
data class AgentConfigureResult(
    val agent: Agent,
)

@Serializable
data class AgentStartResult(
    val agent: Agent,
)

@Serializable
data class FileEntry(
    val name: String,
    val dir: Boolean,
    val size: Long,
    val mtime_ms: Long,
)

@Serializable
data class FilesListResult(
    val entries: List<FileEntry> = emptyList(),
)

@Serializable
data class FilesReadResult(
    val text: String,
    val lines: Int,
    val truncated: Boolean,
)

@Serializable
data class FilesUploadResult(
    val path: String,
)

@Serializable
data class GitFileStatus(
    val path: String,
    val status: String,
    val add: Int,
    val del: Int,
)

@Serializable
data class GitStatusResult(
    val branch: String,
    val ahead: Int,
    val behind: Int,
    val upstream: Boolean = false,
    val files: List<GitFileStatus> = emptyList(),
)

@Serializable
data class GitDiffResult(
    val diff: String,
)

@Serializable
data class GitCommitResult(
    val commit: String,
)

@Serializable
data class UsageBucket(
    val input: Long = 0,
    val output: Long = 0,
    val cache_read: Long = 0,
    val cache_write: Long = 0,
    val cost_usd: Double? = null,
)

@Serializable
data class UsageDayRow(
    val day: String,
    val input: Long = 0,
    val output: Long = 0,
    val cache_read: Long = 0,
    val cache_write: Long = 0,
    val cost_usd: Double? = null,
)

@Serializable
data class UsageAgentRow(
    val id: String,
    val kind: String,
    val title: String,
    val has_data: Boolean = false,
    val total: UsageBucket = UsageBucket(),
)

@Serializable
data class UsageSummaryResult(
    val days: Int = 7,
    val today: UsageBucket = UsageBucket(),
    val period: UsageBucket = UsageBucket(),
    val daily: List<UsageDayRow> = emptyList(),
    val agents: List<UsageAgentRow> = emptyList(),
)

@Serializable
data class ServerEntry(
    val port: Int,
    val pid: Int,
    val command: String,
    val cwd: String,
)

@Serializable
data class ServersListResult(
    val servers: List<ServerEntry> = emptyList(),
)

@Serializable
data class PreviewOpenResult(
    val path: String,
    val expires_ms: Long,
    val session: String,
)

@Serializable
data class WebhookView(
    val id: String,
    val url: String,
    val kinds: List<String> = emptyList(),
)

@Serializable
data class WebhooksListResult(
    val webhooks: List<WebhookView> = emptyList(),
)

@Serializable
data class WebhooksSetResult(
    val webhook: WebhookView,
    val secret: String = "",
)

@Serializable
data class FontEntry(
    val path: String,
    val family: String,
)

@Serializable
data class FontsListResult(
    val fonts: List<FontEntry> = emptyList(),
)

@Serializable
data class RpcErrorEnvelope(
    val kind: String,
    val msg: String? = null,
)

@Serializable
data class GitCommitResponse(
    val commit: String? = null,
    val e: RpcErrorEnvelope? = null,
)

@Serializable
data class TermFrame(
    val t: String,
    val b: String? = null,
    val done: Boolean = false,
    val full: Boolean = false,
)

@Serializable
data class TermOpenResult(val sid: String)
