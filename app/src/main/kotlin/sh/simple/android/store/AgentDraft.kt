package sh.simple.android.store

import sh.simple.android.model.AgentModelsResult
import sh.simple.android.ui.model.modeMenuLabel

data class AgentDraftReset(
    val model: String?,
    val mode: String,
    val modeLabel: String,
    val modelLabel: String,
    val modeOptions: List<String>,
)

fun resetAgentDraftForCatalog(
    catalog: AgentModelsResult,
    kind: String,
    keepModel: String? = null,
    keepMode: String? = null,
): AgentDraftReset {
    val modeOptions = catalog.modes
    val mode = keepMode?.takeIf { want -> modeOptions.any { it.equals(want, ignoreCase = true) } }
        ?: modeOptions.firstOrNull()?.lowercase()
        ?: "ask"
    val model = when {
        kind == "shell" -> null
        catalog.models.isEmpty() -> null
        else -> keepModel?.takeIf { want -> catalog.models.any { it.id == want } } ?: catalog.models.first().id
    }
    return AgentDraftReset(
        model = model,
        mode = mode,
        modeLabel = modeMenuLabel(mode),
        modelLabel = modelDisplayLabel(catalog, model),
        modeOptions = modeOptions,
    )
}

fun startModelForKind(kind: String, catalog: AgentModelsResult, draftModel: String?): String? {
    if (kind == "shell") return null
    if (catalog.models.isEmpty()) return draftModel?.takeIf { it.isNotBlank() }
    val ids = catalog.models.map { it.id }.toSet()
    val pick = draftModel?.takeIf { it in ids } ?: catalog.models.first().id
    return if (pick in ids) pick else catalog.models.first().id
}
