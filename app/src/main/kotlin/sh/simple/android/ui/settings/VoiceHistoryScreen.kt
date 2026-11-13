package sh.simple.android.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import sh.simple.android.ui.glass.GlassStage
import sh.simple.android.ui.kit.AppText
import sh.simple.android.ui.kit.ActionRow
import sh.simple.android.ui.kit.CircleButton
import sh.simple.android.ui.kit.HeaderBar
import sh.simple.android.ui.kit.Icons
import sh.simple.android.ui.kit.SecondaryButton
import sh.simple.android.ui.kit.listScrollBottomPadding
import sh.simple.android.ui.theme.Tokens
import sh.simple.android.ui.theme.Type
import sh.simple.android.voice.VoiceHistoryStore
import java.text.DateFormat
import java.util.Date

@Composable
fun VoiceHistoryScreen(
    store: VoiceHistoryStore,
    onBack: () -> Unit,
    onInsert: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val timeFmt = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    GlassStage(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            HeaderBar(listOf(Icons.Back), "Voice history", emptyList(), onLeftClick = { _ -> onBack() })
            Column(Modifier.fillMaxSize()) {
                if (store.entries.isEmpty()) {
                    AppText(
                        "Recent transcripts appear here after dictation.",
                        Modifier.padding(20.dp),
                        style = Type.body,
                        color = Tokens.current.muted,
                    )
                }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = listScrollBottomPadding(),
                ) {
                    items(store.entries, key = { it.id }) { entry ->
                        val whenLabel = timeFmt.format(Date(entry.createdMs))
                        ActionRow(entry.text, "$whenLabel · Tap to insert", onClick = {
                            store.queueInsert(entry.text)
                            onInsert(entry.text)
                            onBack()
                        }) {
                            CircleButton(Icons.Copy, glass = false, onClick = { copyText(context, entry.text) }, contentDescription = "Copy")
                            CircleButton(Icons.Trash, glass = false, onClick = { store.delete(entry.id) }, contentDescription = "Delete")
                        }
                    }
                }
                if (store.entries.isNotEmpty()) {
                    SecondaryButton("Clear all", Modifier.padding(20.dp)) { store.clearAll() }
                }
            }
        }
    }
}

private fun copyText(context: Context, text: String) {
    val cm = context.getSystemService(ClipboardManager::class.java) ?: return
    cm.setPrimaryClip(ClipData.newPlainText("voice", text))
}
