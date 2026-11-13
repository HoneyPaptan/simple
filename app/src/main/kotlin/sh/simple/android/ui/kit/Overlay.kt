package sh.simple.android.ui.kit

import sh.simple.android.ui.theme.Tokens
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.currentCompositionLocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

class OverlayHost internal constructor() {
    internal val entries = mutableStateListOf<Entry>()
    internal class Entry(val content: @Composable () -> Unit)
}

private val LocalOverlayHost = staticCompositionLocalOf<OverlayHost?> { null }

@Composable
fun OverlayRoot(content: @Composable () -> Unit) {
    val host = remember { OverlayHost() }
    CompositionLocalProvider(LocalOverlayHost provides host) {
        Box(Modifier.fillMaxSize().background(Tokens.current.background)) {
            content()
            Box(Modifier.fillMaxSize().imePadding()) {
                for (entry in host.entries) key(entry) { entry.content() }
            }
        }
    }
}

@Composable
fun Overlay(content: @Composable () -> Unit) {
    val host = requireNotNull(LocalOverlayHost.current) { "Overlay needs OverlayRoot" }
    val locals by rememberUpdatedState(currentCompositionLocalContext)
    val latest by rememberUpdatedState(content)
    val entry = remember { OverlayHost.Entry { CompositionLocalProvider(locals) { latest() } } }
    DisposableEffect(entry) {
        host.entries.add(entry)
        onDispose { host.entries.remove(entry) }
    }
}
