package sh.simple.android.store

import kotlinx.serialization.encodeToString
import sh.simple.android.link.Hosts
import sh.simple.android.model.AgentModelsResult
import sh.simple.android.model.ModelInfo
import sh.simple.android.model.WireJson
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object Models {
    suspend fun load(hosts: Hosts, db: SimpleDatabase, hostId: String, kind: String): AgentModelsResult {
        val cached = db.modelCatalog().get(hostId, kind)
        if (cached != null) {
            return runCatching { WireJson.decodeFromString<AgentModelsResult>(cached.json) }.getOrNull()
                ?: fetch(hosts, db, hostId, kind)
        }
        return fetch(hosts, db, hostId, kind)
    }

    private suspend fun fetch(
        hosts: Hosts,
        db: SimpleDatabase,
        hostId: String,
        kind: String,
    ): AgentModelsResult {
        val raw = hosts.rpc(
            hostId,
            "agent.models",
            buildJsonObject { put("kind", kind) },
        )
        val result = WireJson.decodeFromJsonElement(AgentModelsResult.serializer(), raw)
        db.modelCatalog().upsert(
            ModelCatalogEntity(hostId, kind, WireJson.encodeToString(result)),
        )
        return result
    }
}

fun modelDisplayLabel(catalog: AgentModelsResult, modelId: String?): String {
    if (modelId.isNullOrBlank()) return "Model"
    return catalog.models.firstOrNull { it.id == modelId }?.label ?: modelId
}

fun effortQualifier(effort: String?): String {
    return when (effort?.lowercase()) {
        "low" -> "Fast"
        "medium" -> "Medium"
        "high" -> "High"
        "max" -> "Max"
        else -> "High"
    }
}

private val providerLabels = mapOf(
    "opencode" to "OpenCode Zen",
    "openrouter" to "OpenRouter",
    "anthropic" to "Anthropic",
    "openai" to "OpenAI",
    "google" to "Google",
    "github-copilot" to "GitHub Copilot",
)

fun modelProviderLabel(modelId: String): String {
    val prefix = modelId.substringBefore('/', "")
    if (prefix.isEmpty() || prefix == modelId) return ""
    return providerLabels[prefix] ?: prefix.replaceFirstChar { it.uppercase() }
}

fun modelSearchMatches(info: ModelInfo, query: String): Boolean {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return true
    if (info.label.lowercase().contains(q)) return true
    if (info.id.lowercase().contains(q)) return true
    return modelProviderLabel(info.id).lowercase().contains(q)
}

fun modelRowSubtitle(info: ModelInfo, effort: String?): String {
    val provider = modelProviderLabel(info.id).ifEmpty {
        info.id.substringBefore('/', "").replaceFirstChar { it.uppercase() }
    }
    val level = if (info.efforts.isNotEmpty()) effortQualifier(effort) else ""
    return listOf(provider, level).filter { it.isNotEmpty() }.joinToString(" · ")
}
