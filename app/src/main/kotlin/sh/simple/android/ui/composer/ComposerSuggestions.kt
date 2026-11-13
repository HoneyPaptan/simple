package sh.simple.android.ui.composer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import sh.simple.android.ui.glass.rememberPress
import sh.simple.android.ui.kit.AppText
import sh.simple.android.ui.kit.pressScale
import sh.simple.android.ui.theme.Tokens
import sh.simple.android.ui.theme.Type

sealed class ComposerSuggestionRow {
    abstract val key: String

    data class Slash(val name: String, val description: String) : ComposerSuggestionRow() {
        override val key: String = name
    }

    data class File(val path: String) : ComposerSuggestionRow() {
        override val key: String = path
    }
}

@Composable
fun ComposerSuggestionList(
    rows: List<ComposerSuggestionRow>,
    onPick: (ComposerSuggestionRow) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (rows.isEmpty()) return
    val scroll = rememberScrollState()
    Column(
        modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .heightIn(max = 32.dp * 5)
            .verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for (row in rows) {
            ComposerSuggestionRowView(row, onPick)
        }
    }
}

@Composable
private fun ComposerSuggestionRowView(row: ComposerSuggestionRow, onPick: (ComposerSuggestionRow) -> Unit) {
    val (source, pressed) = rememberPress()
    val primary: String
    val secondary: String
    when (row) {
        is ComposerSuggestionRow.Slash -> {
            primary = "/${row.name}"
            secondary = row.description
        }
        is ComposerSuggestionRow.File -> {
            val (base, folder) = fileSuggestionLabels(row.path)
            primary = base
            secondary = folder
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .pressScale(pressed)
            .clickable(source, null, role = Role.Button, onClick = { onPick(row) })
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppText(primary, Modifier.widthIn(max = 220.dp), style = Type.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
        AppText(secondary, Modifier.weight(1f), style = Type.meta, color = Tokens.current.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

fun filterSlashSuggestions(commands: List<Pair<String, String>>, query: String): List<ComposerSuggestionRow.Slash> {
    val q = query.lowercase()
    return commands
        .filter { (name, _) -> q.isEmpty() || name.lowercase().contains(q) }
        .sortedBy { (name, _) -> !name.lowercase().startsWith(q) }
        .map { (name, desc) -> ComposerSuggestionRow.Slash(name, desc) }
}

fun filterFileSuggestions(paths: List<String>, query: String): List<ComposerSuggestionRow.File> {
    val q = query.lowercase()
    return paths.mapNotNull { path ->
        val match = q.isEmpty() ||
            path.lowercase().contains(q) ||
            path.substringAfterLast('/').lowercase().contains(q)
        if (match) ComposerSuggestionRow.File(path) else null
    }
}
