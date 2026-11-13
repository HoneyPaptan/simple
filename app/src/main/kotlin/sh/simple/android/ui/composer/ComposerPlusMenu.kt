package sh.simple.android.ui.composer

import androidx.compose.runtime.Composable
import sh.simple.android.ui.kit.AnchoredMenu
import sh.simple.android.ui.kit.Icons
import sh.simple.android.ui.kit.MenuEntry
import androidx.compose.ui.geometry.Rect

@Composable
fun ComposerPlusMenu(
    open: Boolean,
    onDismiss: () -> Unit,
    anchor: Rect,
    onPhoto: () -> Unit,
    onCamera: () -> Unit,
) {
    AnchoredMenu(
        open = open,
        onDismiss = onDismiss,
        anchorBounds = anchor,
        items = listOf(
            MenuEntry("Photo", Icons.Image) {
                onDismiss()
                onPhoto()
            },
            MenuEntry("Camera", Icons.Camera) {
                onDismiss()
                onCamera()
            },
        ),
    )
}
