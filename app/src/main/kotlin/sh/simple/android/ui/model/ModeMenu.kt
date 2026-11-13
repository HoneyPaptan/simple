package sh.simple.android.ui.model

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import sh.simple.android.ui.kit.AnchoredMenu
import sh.simple.android.ui.kit.MenuEntry

fun modeMenuLabel(mode: String): String {
    return when (mode.lowercase()) {
        "ask" -> "Ask"
        "auto" -> "Auto"
        "full" -> "Full control"
        "plan" -> "Plan"
        else -> mode.replaceFirstChar { it.uppercase() }
    }
}

@Composable
fun PermissionModeMenu(
    open: Boolean,
    onDismiss: () -> Unit,
    anchorBounds: Rect,
    modes: List<String>,
    current: String?,
    onPick: (String) -> Unit,
) {
    val active = current?.lowercase()
    AnchoredMenu(
        open = open,
        onDismiss = onDismiss,
        anchorBounds = anchorBounds,
        items = modes.map { mode ->
            MenuEntry(
                modeMenuLabel(mode),
                checked = mode.lowercase() == active,
                action = {
                    onPick(mode)
                    onDismiss()
                },
            )
        },
    )
}
