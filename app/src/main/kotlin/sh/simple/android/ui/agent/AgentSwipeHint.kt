package sh.simple.android.ui.agent

import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import sh.simple.android.ui.kit.AppText
import sh.simple.android.ui.kit.HeaderButtonSize
import sh.simple.android.ui.theme.Tokens
import sh.simple.android.ui.theme.Type

private const val PREFS = "agent_swipe"
private const val KEY_HINT_SEEN = "swipe_hint_seen"

object AgentSwipeHintPrefs {
    fun shouldShow(context: Context): Boolean =
        !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_HINT_SEEN, false)

    fun markSeen(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_HINT_SEEN, true).apply()
    }
}

@Composable
fun AgentSwipeHintLine(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var visible by remember { mutableStateOf(AgentSwipeHintPrefs.shouldShow(context)) }
    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        delay(3000)
        visible = false
        AgentSwipeHintPrefs.markSeen(context)
    }
    if (!visible) return
    AppText(
        "Swipe for other agents",
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 14.dp + HeaderButtonSize, bottom = 4.dp),
        style = Type.meta,
        color = Tokens.current.faint,
        textAlign = TextAlign.Center,
    )
}
