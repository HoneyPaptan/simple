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
import sh.simple.android.ui.glass.GlassStage
import sh.simple.android.ui.kit.AppText
import sh.simple.android.ui.kit.GlassSheet
import sh.simple.android.ui.kit.ActionRow
import sh.simple.android.ui.kit.CircleButton
import sh.simple.android.ui.kit.HeaderBar
import sh.simple.android.ui.kit.Icons
import sh.simple.android.ui.kit.ListRow
import sh.simple.android.ui.kit.PrimaryButton
import sh.simple.android.ui.kit.SectionLabel
import sh.simple.android.ui.kit.listScrollBottomPadding
import sh.simple.android.ui.theme.Tokens
import sh.simple.android.ui.theme.Type
import sh.simple.android.voice.VoiceReplacement
import sh.simple.android.voice.VoiceReplacementsStore

@Composable
fun VoiceReplacementsScreen(
    store: VoiceReplacementsStore,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var editTarget by remember { mutableStateOf<VoiceReplacement?>(null) }
    var heardField by remember { mutableStateOf("") }
    var writeField by remember { mutableStateOf("") }
    var sheetOpen by remember { mutableStateOf(false) }
    fun openSheet(rule: VoiceReplacement?) {
        editTarget = rule
        heardField = rule?.heard.orEmpty()
        writeField = rule?.write.orEmpty()
        sheetOpen = true
    }
    GlassStage(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            HeaderBar(listOf(Icons.Back), "Replacements", emptyList(), onLeftClick = { _ -> onBack() })
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = listScrollBottomPadding().calculateBottomPadding()),
            ) {
                AppText(
                    "Whole word swaps after transcription. Matching ignores case.",
                    Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    style = Type.meta,
                    color = Tokens.current.muted,
                )
                for (rule in store.rules) {
                    ActionRow(rule.heard, "Write as ${rule.write}", onClick = { openSheet(rule) }) {
                        CircleButton(Icons.Trash, glass = false, onClick = { store.delete(rule.id) }, contentDescription = "Delete")
                    }
                }
                SectionLabel("Add")
                ListRow(Icons.Plus, "New replacement", onClick = { openSheet(null) })
            }
        }
    }
    GlassSheet(
        visible = sheetOpen,
        onDismiss = { sheetOpen = false },
        title = if (editTarget == null) "New replacement" else "Edit replacement",
        form = true,
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            AppText("Heard", style = Type.meta, color = Tokens.current.muted)
            VoiceField(heardField, { heardField = it }, Modifier.padding(top = 6.dp))
            AppText("Write as", Modifier.padding(top = 16.dp), style = Type.meta, color = Tokens.current.muted)
            VoiceField(writeField, { writeField = it }, Modifier.padding(top = 6.dp))
            PrimaryButton("Save", Modifier.padding(top = 20.dp)) {
                val target = editTarget
                if (target == null) {
                    store.add(heardField, writeField)
                } else {
                    store.update(target.id, heardField, writeField)
                }
                sheetOpen = false
            }
        }
    }
}

@Composable
private fun VoiceField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier
            .fillMaxWidth()
            .clip(Capsule())
            .background(Tokens.current.raised)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        textStyle = Type.body.copy(color = Tokens.current.foreground),
        cursorBrush = SolidColor(Tokens.current.foreground),
        singleLine = true,
    )
}
