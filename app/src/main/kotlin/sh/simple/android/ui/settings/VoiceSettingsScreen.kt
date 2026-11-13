package sh.simple.android.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import sh.simple.android.ui.glass.GlassStage
import sh.simple.android.ui.kit.AppText
import sh.simple.android.ui.kit.CenterDialog
import sh.simple.android.ui.kit.CircleButton
import sh.simple.android.ui.kit.DialogButtonSpec
import sh.simple.android.ui.kit.GlassSheet
import sh.simple.android.ui.kit.HeaderBar
import sh.simple.android.ui.kit.Icons
import sh.simple.android.ui.kit.ListRow
import sh.simple.android.ui.kit.SectionLabel
import sh.simple.android.ui.kit.listScrollBottomPadding
import sh.simple.android.ui.kit.SimpleIcon
import sh.simple.android.ui.theme.Tokens
import sh.simple.android.ui.theme.Type
import sh.simple.android.voice.VoiceCatalog
import sh.simple.android.voice.VoiceDownloadState
import sh.simple.android.voice.VoiceHistoryStore
import sh.simple.android.voice.VoiceModelSpec
import sh.simple.android.voice.VoiceModelStore
import sh.simple.android.voice.VoiceReplacementsStore
import kotlin.math.roundToInt

private enum class VoiceSubpage { Main, Replacements, History }

@Composable
fun VoiceSettingsScreen(
    store: VoiceModelStore,
    replacements: VoiceReplacementsStore,
    history: VoiceHistoryStore,
    onBack: () -> Unit,
    onInsertFromHistory: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var subpage by remember { mutableStateOf(VoiceSubpage.Main) }
    when (subpage) {
        VoiceSubpage.Replacements -> VoiceReplacementsScreen(replacements, { subpage = VoiceSubpage.Main }, modifier)
        VoiceSubpage.History -> VoiceHistoryScreen(history, { subpage = VoiceSubpage.Main }, onInsertFromHistory, modifier)
        VoiceSubpage.Main -> VoiceMainScreen(
            store = store,
            replacements = replacements,
            history = history,
            onBack = onBack,
            onOpenReplacements = { subpage = VoiceSubpage.Replacements },
            onOpenHistory = { subpage = VoiceSubpage.History },
            modifier = modifier,
        )
    }
}

@Composable
private fun VoiceMainScreen(
    store: VoiceModelStore,
    replacements: VoiceReplacementsStore,
    history: VoiceHistoryStore,
    onBack: () -> Unit,
    onOpenReplacements: () -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var cellularPrompt by remember { mutableStateOf<String?>(null) }
    GlassSheet(
        visible = cellularPrompt != null,
        onDismiss = { cellularPrompt = null },
        title = "Mobile data",
        doneLabel = "Cancel",
        onDone = { cellularPrompt = null },
    ) {
        AppText(
            "Whisper models are large. Download on Wi Fi when you can.",
            Modifier.padding(20.dp),
            style = Type.body,
        )
        ListRow(Icons.Check, "Download anyway") {
            val id = cellularPrompt ?: return@ListRow
            cellularPrompt = null
            store.startDownload(id, allowCellular = true) {}
        }
    }
    GlassStage(modifier.fillMaxSize().background(Tokens.current.background)) {
        Column(Modifier.fillMaxSize()) {
            HeaderBar(listOf(Icons.Back), "Voice", emptyList(), onLeftClick = { _ -> onBack() })
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = listScrollBottomPadding(),
            ) {
                item {
                    SectionLabel("Model")
                    AppText(
                        "Speech runs on this phone only. Pick a model, then download it once.",
                        Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        style = Type.meta,
                        color = Tokens.current.muted,
                    )
                }
                items(VoiceCatalog.models, key = { it.id }) { spec ->
                    ModelRow(
                        spec = spec,
                        selected = store.selectedId == spec.id,
                        state = store.downloadStates[spec.id] ?: VoiceDownloadState.Idle,
                        onSelect = { store.setSelected(spec.id) },
                        onDownload = {
                            store.startDownload(spec.id, allowCellular = false) {
                                cellularPrompt = spec.id
                            }
                        },
                        onCancel = { store.cancelDownload(spec.id) },
                        onDelete = { store.deleteModel(spec.id) },
                    )
                }
                item {
                    SectionLabel("Replacements")
                    ListRow(
                        Icons.Sliders,
                        "Replacements",
                        secondary = "${replacements.rules.size} rules",
                        onClick = onOpenReplacements,
                    )
                }
                item {
                    SectionLabel("Voice history")
                    ListRow(
                        Icons.Mic,
                        "Voice history",
                        secondary = "${history.entries.size} saved",
                        onClick = onOpenHistory,
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelRow(
    spec: VoiceModelSpec,
    selected: Boolean,
    state: VoiceDownloadState,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    var deleteOpen by remember { mutableStateOf(false) }
    val sizeMb = (spec.bytes / (1024.0 * 1024.0)).roundToInt()
    val secondary = when (state) {
        VoiceDownloadState.Idle -> "${sizeMb} MiB · Not downloaded"
        is VoiceDownloadState.Running -> {
            val pct = if (state.total > 0) ((100.0 * state.received) / state.total).roundToInt() else 0
            "Downloading $pct%"
        }
        VoiceDownloadState.Ready -> "${sizeMb} MiB · Downloaded"
        is VoiceDownloadState.Failed -> state.reason
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .clickableRow(onSelect),
            ) {
                AppText(spec.label, style = Type.rowLabel, maxLines = 2)
                AppText(
                    secondary,
                    Modifier.padding(top = 4.dp),
                    style = Type.meta,
                    color = Tokens.current.muted,
                    maxLines = 2,
                )
                if (state is VoiceDownloadState.Running && state.total > 0) {
                    Box(
                        Modifier
                            .padding(top = 6.dp)
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(Tokens.current.raised),
                    ) {
                        val frac = (state.received.toFloat() / state.total.toFloat()).coerceIn(0f, 1f)
                        Box(
                            Modifier
                                .fillMaxWidth(frac)
                                .height(4.dp)
                                .background(Tokens.current.primaryFill),
                        )
                    }
                }
            }
            if (selected) {
                SimpleIcon(Icons.Check, size = 18.dp, tint = Tokens.current.foreground)
            }
            when (state) {
                VoiceDownloadState.Idle, is VoiceDownloadState.Failed -> {
                    CircleButton(Icons.Down, glass = false, contentDescription = "Download", onClick = onDownload)
                }
                is VoiceDownloadState.Running -> {
                    CircleButton(Icons.Stop, glass = false, contentDescription = "Cancel download", onClick = onCancel)
                }
                VoiceDownloadState.Ready -> {
                    CircleButton(Icons.Trash, glass = false, contentDescription = "Delete", onClick = { deleteOpen = true })
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(start = 51.dp)
                .height(1.dp)
                .background(Tokens.current.hairline),
        )
    }
    if (deleteOpen) {
        CenterDialog(
            title = "Delete model?",
            visible = true,
            onDismiss = { deleteOpen = false },
            body = spec.label,
            buttons = listOf(
                DialogButtonSpec("Cancel") { deleteOpen = false },
                DialogButtonSpec("Delete", destructive = true) {
                    deleteOpen = false
                    onDelete()
                },
            ),
        )
    }
}

private fun Modifier.clickableRow(onClick: () -> Unit): Modifier = clickable(onClick = onClick)
