package sh.simple.android.ui.chat

import android.content.pm.ApplicationInfo
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import sh.simple.android.ui.glass.GlassBudgetDebug
import sh.simple.android.ui.kit.AppText
import sh.simple.android.ui.theme.Tokens
import sh.simple.android.ui.theme.Type

object ChatOpenTrace {
    var overlayEnabled: Boolean
        get() = GlassBudgetDebug.overlayEnabled
        set(value) {
            GlassBudgetDebug.overlayEnabled = value
        }

    var lastOpenMs: Long? = null
}

@Composable
fun ChatOpenTraceEffect(contentReady: Boolean) {
    val started = remember { SystemClock.elapsedRealtime() }
    var done by remember { mutableStateOf(false) }
    LaunchedEffect(contentReady) {
        if (done || !contentReady) return@LaunchedEffect
        ChatOpenTrace.lastOpenMs = SystemClock.elapsedRealtime() - started
        done = true
    }
}

@Composable
fun ChatOpenTraceOverlay(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    if (!debuggable || !ChatOpenTrace.overlayEnabled) return
    val ms = ChatOpenTrace.lastOpenMs ?: return
    Box(
        modifier
            .padding(top = 52.dp, end = 8.dp)
            .background(Tokens.current.raised.copy(alpha = 0.92f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        AppText("Open ${ms}ms", style = Type.meta, color = Tokens.current.foreground)
    }
}
