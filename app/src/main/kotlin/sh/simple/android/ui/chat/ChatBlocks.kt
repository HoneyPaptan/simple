package sh.simple.android.ui.chat

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import sh.simple.android.model.Message
import sh.simple.android.model.Tool

sealed interface ChatBlock {
    val key: String

    data class User(
        override val key: String,
        val text: String,
        val imagePaths: List<String> = emptyList(),
        val outbound: Boolean = false,
        val statusLabel: String? = null,
    ) : ChatBlock
    data class Assistant(override val key: String, val text: String) : ChatBlock
    data class Tools(override val key: String, val tools: List<ToolEntry>) : ChatBlock
    data class Thinking(override val key: String, val text: String) : ChatBlock
    data class Marker(override val key: String, val text: String) : ChatBlock
}

enum class ToolKind { Read, Search, Command, Edit, Todo, Agent, Web, Other }

data class TodoItem(val text: String, val status: String)

data class ToolEntry(
    val id: String,
    val kind: ToolKind,
    val verb: String,
    val detail: String,
    val title: String,
    val status: String,
    val command: String,
    val output: String,
    val diff: String,
    val todos: List<TodoItem>,
    val ts: Long = 0L,
) {
    val running: Boolean get() = status == "running"
    val failed: Boolean get() = status == "error"
}

private val toolJson = Json { ignoreUnknownKeys = true }

fun chatBlocks(messages: List<Message>, working: Boolean = false): List<ChatBlock> {
    val out = ArrayList<ChatBlock>()
    var pending = ArrayList<ToolEntry>()
    var turnStart: Message? = null
    fun flush() {
        if (pending.isEmpty()) return
        out += ChatBlock.Tools("tools:" + pending.first().id, pending)
        pending = ArrayList()
    }
    fun closeTurn() {
        turnStart = null
    }
    for (msg in messages) {
        val tool = msg.tool
        when {
            msg.role == "tool" && tool != null -> {
                pending += toolEntry(msg.id, tool, msg.ts)
            }
            msg.role == "user" -> {
                flush()
                closeTurn()
                val raw = msg.text.orEmpty()
                val stripped = stripImageMarkers(raw)
                out += ChatBlock.User(msg.id, stripped.ifEmpty { raw })
                turnStart = msg
            }
            msg.role == "assistant" && !msg.text.isNullOrBlank() -> {
                flush()
                out += ChatBlock.Assistant(msg.id, msg.text)
            }
            msg.role == "thinking" && !msg.text.isNullOrBlank() -> {
                flush()
                out += ChatBlock.Thinking(msg.id, msg.text)
            }
            msg.role == "event" && !msg.text.isNullOrBlank() -> {
                flush()
                out += ChatBlock.Marker(msg.id, msg.text)
                turnStart = null
            }
        }
    }
    flush()
    if (!working) closeTurn()
    return out
}

fun workedLabel(ms: Long): String? {
    val seconds = ms / 1000
    return when {
        seconds < 5 -> null
        seconds < 60 -> "Worked for ${seconds}s"
        seconds < 3600 -> "Worked for ${seconds / 60}m ${seconds % 60}s"
        else -> "Worked for ${seconds / 3600}h ${seconds % 3600 / 60}m"
    }
}

fun toolKind(name: String): ToolKind = when (name.lowercase()) {
    "read", "read_file", "ls", "list", "notebookread" -> ToolKind.Read
    "grep", "glob", "search", "find", "codebase_search" -> ToolKind.Search
    "bash", "shell", "exec_command", "local_shell", "bashoutput", "killshell" -> ToolKind.Command
    "edit", "write", "multiedit", "notebookedit", "apply_patch", "patch" -> ToolKind.Edit
    "todowrite", "update_plan" -> ToolKind.Todo
    "task", "agent" -> ToolKind.Agent
    "webfetch", "websearch", "web_search", "fetch" -> ToolKind.Web
    else -> ToolKind.Other
}

fun toolEntry(id: String, tool: Tool, ts: Long = 0L): ToolEntry {
    val kind = toolKind(tool.name)
    val input = parseInput(tool.input)
    val summary = tool.summary.ifEmpty { tool.name }
    val command = if (kind == ToolKind.Command) input.string("command") else ""
    return ToolEntry(
        id = id,
        kind = kind,
        verb = if (tool.status == "denied") "Denied" else toolVerb(kind, tool.name),
        detail = command.lineSequence().firstOrNull()?.takeIf { it.isNotBlank() } ?: summary,
        title = summary,
        status = tool.status,
        command = command,
        output = tool.output.orEmpty().trimEnd(),
        diff = tool.diff.orEmpty(),
        todos = if (kind == ToolKind.Todo) todoItems(input) else emptyList(),
        ts = ts,
    )
}

data class TurnFileChange(val path: String, val adds: Int, val removes: Int)

fun turnFileChanges(tools: List<ToolEntry>): List<TurnFileChange> {
    val merged = linkedMapOf<String, Pair<Int, Int>>()
    for (tool in tools) {
        if (tool.kind != ToolKind.Edit || tool.failed) continue
        val path = tool.detail.ifBlank { tool.title }.trim()
        if (path.isEmpty()) continue
        val lines = diffLines(tool.diff)
        val prev = merged[path] ?: (0 to 0)
        merged[path] = prev.first + lines.count { it.first == '+' } to prev.second + lines.count { it.first == '-' }
    }
    return merged.map { TurnFileChange(it.key, it.value.first, it.value.second) }
}

fun turnToolEntries(segments: List<ChatBlock>): List<ToolEntry> =
    segments.filterIsInstance<ChatBlock.Tools>().flatMap { it.tools }

fun turnStepCount(segments: List<ChatBlock>): Int {
    val tools = turnToolEntries(segments).size
    val thinking = segments.count { it is ChatBlock.Thinking }
    return tools + thinking
}

fun turnWorkDurationMs(tools: List<ToolEntry>): Long? {
    val stamped = tools.filter { it.ts > 0L }
    if (stamped.size < 2) return null
    return stamped.maxOf { it.ts } - stamped.minOf { it.ts }
}

fun workedPillLabel(steps: Int, durationMs: Long?): String {
    val count = plural(steps, "step")
    if (durationMs == null || durationMs < 5000) return "Worked $count"
    val seconds = durationMs / 1000
    val time = when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m"
        else -> "${seconds / 3600}h ${seconds % 3600 / 60}m"
    }
    return "Worked $time · $count"
}

private fun toolVerb(kind: ToolKind, name: String): String = when (kind) {
    ToolKind.Read -> "Read"
    ToolKind.Search -> "Searched"
    ToolKind.Command -> "Ran"
    ToolKind.Edit -> if (name.equals("Write", ignoreCase = true)) "Wrote" else "Edited"
    ToolKind.Todo -> "Updated todos"
    ToolKind.Agent -> "Agent"
    ToolKind.Web -> "Fetched"
    ToolKind.Other -> name
}

fun toolGroupLabel(all: List<ToolEntry>): String {
    val denied = all.count { it.status == "denied" }
    val tools = all.filter { it.status != "denied" }
    val counts = tools.groupingBy { it.kind }.eachCount()
    fun files(kind: ToolKind) = tools.filter { it.kind == kind }.map { it.detail }.distinct().size.takeIf { it > 0 }
    val explored = listOfNotNull(
        files(ToolKind.Read)?.let { plural(it, "file") },
        counts[ToolKind.Search]?.let { plural(it, "search", "searches") },
    )
    val parts = buildList {
        if (explored.isNotEmpty()) add("Explored " + explored.joinToString(", "))
        counts[ToolKind.Command]?.let { add("ran " + plural(it, "command")) }
        files(ToolKind.Edit)?.let { add("edited " + plural(it, "file")) }
        counts[ToolKind.Agent]?.let { add("started " + plural(it, "agent")) }
        counts[ToolKind.Web]?.let { add("fetched " + plural(it, "page")) }
        val other = (counts[ToolKind.Other] ?: 0) + (counts[ToolKind.Todo] ?: 0)
        if (other > 0) add("used " + plural(other, "other tool"))
        if (denied > 0) add("denied " + plural(denied, "request"))
    }
    return parts.joinToString(", ").replaceFirstChar { it.uppercase() }
}

fun liveTool(tools: List<ToolEntry>): ToolEntry? = tools.lastOrNull { it.running }

fun diffLines(diff: String): List<Pair<Char, String>> = diff.lines()
    .filterNot { it.startsWith("---") || it.startsWith("+++") }
    .mapNotNull { line ->
        when {
            line.startsWith("+") -> '+' to line.drop(1)
            line.startsWith("-") -> '-' to line.drop(1)
            else -> null
        }
    }

private fun plural(n: Int, one: String, many: String = one + "s") = "$n ${if (n == 1) one else many}"

private fun parseInput(raw: String?): JsonObject =
    raw?.let { runCatching { toolJson.parseToJsonElement(it) as? JsonObject }.getOrNull() } ?: JsonObject(emptyMap())

private fun JsonObject.string(key: String): String = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()

private fun todoItems(input: JsonObject): List<TodoItem> {
    val list = (input["todos"] ?: input["plan"]) as? JsonArray ?: return emptyList()
    return list.mapNotNull { el ->
        val obj = el as? JsonObject ?: return@mapNotNull null
        val text = obj.string("content").ifEmpty { obj.string("step") }
        if (text.isEmpty()) null else TodoItem(text, obj.string("status"))
    }
}
