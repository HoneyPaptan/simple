package sh.simple.android.ui.model

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.simple.android.link.Hosts
import sh.simple.android.link.RemoteGateway
import sh.simple.android.link.launchRpc
import sh.simple.android.link.rpcErrorMessage
import sh.simple.android.model.AgentModelsResult
import sh.simple.android.model.ModelInfo
import sh.simple.android.store.Models
import sh.simple.android.store.SimpleDatabase
import sh.simple.android.store.effortQualifier
import sh.simple.android.store.modelRowSubtitle
import sh.simple.android.store.modelDisplayLabel
import sh.simple.android.store.modelSearchMatches
import sh.simple.android.ui.kit.AnchoredMenu
import sh.simple.android.ui.kit.AppText
import sh.simple.android.ui.kit.ErrorState
import sh.simple.android.ui.kit.GlassSheet
import sh.simple.android.ui.kit.LoadingRow
import sh.simple.android.ui.kit.Icons
import sh.simple.android.ui.kit.MenuEntry
import sh.simple.android.ui.kit.SectionLabel
import sh.simple.android.ui.kit.SimpleIcon
import sh.simple.android.ui.theme.Tokens
import sh.simple.android.ui.theme.Type

@Composable
fun ModelPickerSheets(
    open: Boolean,
    onDismiss: () -> Unit,
    hosts: Hosts,
    db: SimpleDatabase,
    hostId: String,
    kind: String,
    agentId: String?,
    currentModel: String?,
    currentEffort: String?,
    onDraftChange: (model: String?, effort: String?) -> Unit = { _, _ -> },
) {
    var catalog by remember { mutableStateOf<AgentModelsResult?>(null) }
    var loadError by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    LaunchedEffect(open) { if (!open) search = "" }
    var settingsOpen by remember { mutableStateOf(false) }
    var settingsModel by remember { mutableStateOf<ModelInfo?>(null) }
    var effort by remember { mutableStateOf(currentEffort ?: "high") }
    var effortMenuOpen by remember { mutableStateOf(false) }
    var effortAnchor by remember { mutableStateOf(Rect.Zero) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(open, hostId, kind) {
        if (!open) return@LaunchedEffect
        loadError = ""
        val result = withContext(Dispatchers.IO) { runCatching { Models.load(hosts, db, hostId, kind) } }
        catalog = result.getOrNull()
        if (catalog == null) {
            loadError = result.exceptionOrNull()?.let { rpcErrorMessage(it) }.orEmpty()
        }
    }
    LaunchedEffect(currentEffort) {
        if (currentEffort != null) effort = currentEffort
    }
    val models = catalog?.models.orEmpty()
    val activeId = currentModel?.takeIf { id -> models.any { it.id == id } } ?: models.firstOrNull()?.id
    val query = search.trim()
    fun matches(info: ModelInfo): Boolean = modelSearchMatches(info, query)
    val active = models.filter { it.id == activeId && matches(it) }
    val more = models.filter { it.id != activeId && matches(it) }
    fun pickModel(info: ModelInfo) {
        if (agentId != null) {
            scope.launchRpc {
                RemoteGateway.configure(hosts, hostId, agentId, model = info.id)
                onDraftChange(info.id, effort)
                onDismiss()
            }
        } else {
            onDraftChange(info.id, effort)
            onDismiss()
        }
    }
    GlassSheet(open, onDismiss, "Model") {
        Column(Modifier.fillMaxWidth()) {
            if (loadError.isNotEmpty()) {
                ErrorState(loadError)
            } else if (catalog == null) {
                LoadingRow(lines = 4)
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SimpleIcon(Icons.Search, size = 18.dp, tint = Tokens.current.muted)
                BasicTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.weight(1f),
                    textStyle = Type.body.copy(color = Tokens.current.foreground),
                    cursorBrush = SolidColor(Tokens.current.foreground),
                    singleLine = true,
                    decorationBox = { inner ->
                        if (search.isEmpty()) {
                            AppText("Search", style = Type.body, color = Tokens.current.muted, maxLines = 1)
                        }
                        inner()
                    },
                )
            }
            LazyColumn(Modifier.weight(1f, fill = false)) {
                if (active.isNotEmpty()) {
                    item { SectionLabel("Active", Modifier.padding(start = 0.dp)) }
                    items(active, key = { it.id }) { info ->
                        ModelRow(
                            info,
                            modelRowSubtitle(info, effort),
                            selected = true,
                            onPick = { pickModel(info) },
                            onMore = {
                                settingsModel = info
                                settingsOpen = true
                            },
                        )
                    }
                }
                if (more.isNotEmpty()) {
                    item { SectionLabel("More", Modifier.padding(start = 0.dp)) }
                    items(more, key = { it.id }) { info ->
                        ModelRow(
                            info,
                            modelRowSubtitle(info, effort),
                            selected = false,
                            onPick = { pickModel(info) },
                            onMore = {
                                settingsModel = info
                                settingsOpen = true
                            },
                        )
                    }
                }
            }
        }
    }
    val picked = settingsModel
    if (picked != null) {
        GlassSheet(
            settingsOpen,
            { settingsOpen = false },
            "Model settings",
            stacked = true,
            doneLabel = "Done",
            onDone = {
                if (agentId != null) {
                    scope.launchRpc {
                        RemoteGateway.configure(
                            hosts,
                            hostId,
                            agentId,
                            model = picked.id,
                            effort = effort,
                        )
                        onDraftChange(picked.id, effort)
                    }
                } else {
                    onDraftChange(picked.id, effort)
                }
                settingsOpen = false
            },
        ) {
            Column(Modifier.fillMaxWidth()) {
                AppText(picked.label, style = Type.headline, maxLines = 2)
                AppText(
                    effortQualifier(effort),
                    style = Type.meta,
                    color = Tokens.current.muted,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                )
                Box(Modifier.onGloballyPositioned { effortAnchor = it.boundsInRoot() }) {
                    ModelSettingsRow("Effort", effort.replaceFirstChar { it.uppercase() }) {
                        effortMenuOpen = true
                    }
                }
            }
        }
        val efforts = picked.efforts.ifEmpty { listOf("low", "medium", "high", "max") }
        AnchoredMenu(
            open = effortMenuOpen,
            onDismiss = { effortMenuOpen = false },
            anchorBounds = effortAnchor,
            items = efforts.map { level ->
                MenuEntry(
                    level.replaceFirstChar { it.uppercase() },
                    checked = level == effort,
                    action = {
                        effort = level
                        effortMenuOpen = false
                    },
                )
            },
        )
    }
}

@Composable
private fun ModelRow(
    info: ModelInfo,
    qualifier: String,
    selected: Boolean,
    onPick: () -> Unit,
    onMore: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onPick),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                AppText(info.label, style = Type.rowLabel, maxLines = 1)
                if (qualifier.isNotEmpty()) AppText(qualifier, style = Type.meta, color = Tokens.current.muted, maxLines = 1)
            }
            Row(
                Modifier.clickable(role = Role.Button, onClick = onMore).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SimpleIcon(Icons.More, size = 18.dp, tint = Tokens.current.muted)
                if (selected) {
                    SimpleIcon(Icons.Check, size = 18.dp, tint = Tokens.current.foreground)
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 1.dp)
                .background(Tokens.current.hairline),
        )
    }
}

@Composable
private fun ModelSettingsRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppText(label, Modifier.weight(1f), style = Type.rowLabel, maxLines = 1)
        AppText(value, style = Type.meta, color = Tokens.current.muted, maxLines = 1)
        SimpleIcon(Icons.ChevR, size = 18.dp, tint = Tokens.current.muted)
    }
}
