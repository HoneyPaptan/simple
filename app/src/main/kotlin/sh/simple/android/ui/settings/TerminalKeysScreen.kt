package sh.simple.android.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.kyant.shapes.Capsule
import java.util.UUID
import sh.simple.android.store.TerminalKeyBarStore
import sh.simple.android.store.TerminalKeyEntry
import sh.simple.android.store.TerminalKeyPresets
import sh.simple.android.ui.glass.GlassStage
import sh.simple.android.ui.kit.ActionRow
import sh.simple.android.ui.kit.AppText
import sh.simple.android.ui.kit.CircleButton
import sh.simple.android.ui.kit.GlassSheet
import sh.simple.android.ui.kit.HeaderBar
import sh.simple.android.ui.kit.Icons
import sh.simple.android.ui.kit.ListRow
import sh.simple.android.ui.kit.PrimaryButton
import sh.simple.android.ui.kit.SectionLabel
import sh.simple.android.ui.kit.listScrollBottomPadding
import sh.simple.android.ui.theme.Tokens
import sh.simple.android.ui.theme.Type

@Composable
fun TerminalKeysScreen(
    store: TerminalKeyBarStore,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var presetOpen by remember { mutableStateOf(false) }
    var snippetOpen by remember { mutableStateOf(false) }
    var labelField by remember { mutableStateOf("") }
    var textField by remember { mutableStateOf("") }
    GlassStage(modifier.fillMaxSize().background(Tokens.current.background)) {
        Column(Modifier.fillMaxSize()) {
            HeaderBar(listOf(Icons.Back), "Terminal keys", emptyList(), onLeftClick = { _ -> onBack() })
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = listScrollBottomPadding().calculateBottomPadding()),
            ) {
                AppText(
                    "Shortcuts appear after esc, tab, and the arrow keys on the terminal screen.",
                    Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    style = Type.meta,
                    color = Tokens.current.muted,
                )
                val list = store.entries
                for ((index, entry) in list.withIndex()) {
                    ActionRow(entry.label, entry.spec.takeIf { !it.equals(entry.label, ignoreCase = true) }) {
                        if (index > 0) {
                            CircleButton(Icons.Up, glass = false, onClick = { store.moveUp(entry.id) }, contentDescription = "Move up")
                        }
                        if (index < list.lastIndex) {
                            CircleButton(Icons.Down, glass = false, onClick = { store.moveDown(entry.id) }, contentDescription = "Move down")
                        }
                        CircleButton(Icons.Trash, glass = false, onClick = { store.remove(entry.id) }, contentDescription = "Remove")
                    }
                }
                SectionLabel("Add")
                ListRow(Icons.Plus, "From presets", onClick = { presetOpen = true })
                ListRow(Icons.Text, "Text snippet", onClick = {
                    labelField = ""
                    textField = ""
                    snippetOpen = true
                })
            }
        }
    }
    GlassSheet(visible = presetOpen, onDismiss = { presetOpen = false }, title = "Presets", form = true) {
        Column(Modifier.padding(bottom = 12.dp)) {
            for (preset in TerminalKeyPresets.catalog) {
                ListRow(Icons.Plus, preset.label, chevron = false) {
                    val copy = preset.copy(id = UUID.randomUUID().toString())
                    if (store.entries.none { it.spec == copy.spec && it.label == copy.label }) {
                        store.add(copy)
                    }
                    presetOpen = false
                }
            }
        }
    }
    GlassSheet(visible = snippetOpen, onDismiss = { snippetOpen = false }, title = "Text snippet", form = true) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            AppText("Label", style = Type.meta, color = Tokens.current.muted)
            SnippetField(labelField, { labelField = it }, Modifier.padding(top = 6.dp))
            AppText("Text", Modifier.padding(top = 16.dp), style = Type.meta, color = Tokens.current.muted)
            SnippetField(textField, { textField = it.take(64) }, Modifier.padding(top = 6.dp))
            PrimaryButton("Save", Modifier.padding(top = 20.dp)) {
                store.addSnippet(labelField, textField)
                snippetOpen = false
            }
        }
    }
}

@Composable
private fun SnippetField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier
            .fillMaxWidth()
            .clip(Capsule())
            .background(Tokens.current.raised)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        textStyle = Type.body.copy(color = Tokens.current.foreground),
        cursorBrush = SolidColor(Tokens.current.foreground),
        singleLine = true,
    )
}
