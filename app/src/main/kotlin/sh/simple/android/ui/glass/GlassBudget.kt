package sh.simple.android.ui.glass

import android.content.pm.ApplicationInfo
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import sh.simple.android.ui.kit.AppText
import sh.simple.android.ui.theme.Tokens
import sh.simple.android.ui.theme.Type

fun Context.isDebuggableApp(): Boolean =
    applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

class GlassSurfaceCounter {
    var liveSurfaces by mutableIntStateOf(0)
        private set

    internal fun onSurfaceShown() {
        liveSurfaces += 1
    }

    internal fun onSurfaceHidden() {
        liveSurfaces -= 1
    }
}

val LocalGlassSurfaceCounter = staticCompositionLocalOf { GlassSurfaceCounter() }

object GlassBudgetDebug {
    var overlayEnabled by mutableStateOf(false)
}

@Composable
fun rememberGlassSurfaceCounter(): GlassSurfaceCounter = remember { GlassSurfaceCounter() }

@Composable
internal fun TrackDrawBackdropSurface(active: Boolean) {
    if (!active) return
    val counter = LocalGlassSurfaceCounter.current
    DisposableEffect(counter) {
        counter.onSurfaceShown()
        onDispose { counter.onSurfaceHidden() }
    }
}

@Composable
fun GlassBudgetOverlay(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    if (!context.isDebuggableApp() || !GlassBudgetDebug.overlayEnabled) return
    val counter = LocalGlassSurfaceCounter.current
    val over = counter.liveSurfaces > 5
    Box(
        modifier
            .padding(8.dp)
            .background(
                if (over) Tokens.current.remove.copy(alpha = 0.85f) else Tokens.current.raised.copy(alpha = 0.92f),
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        AppText(
            "Glass ${counter.liveSurfaces}/5",
            style = Type.meta,
            color = if (over) Tokens.current.onPrimary else Tokens.current.foreground,
        )
    }
}
