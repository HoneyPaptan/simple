package sh.simple.android.ui.kit

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kyant.shapes.RoundedRectangle
import kotlin.math.roundToInt
import sh.simple.android.ui.glass.GlassKind
import sh.simple.android.ui.glass.glass
import sh.simple.android.ui.glass.rememberPress
import sh.simple.android.ui.theme.Tokens
import sh.simple.android.ui.theme.Type

class MenuEntry(
    val label: String,
    val icon: ImageVector? = null,
    val detail: String = "",
    val checked: Boolean = false,
    val destructive: Boolean = false,
    val enabled: Boolean = true,
    val action: () -> Unit = {},
)

@Composable
fun AnchoredMenu(
    open: Boolean,
    onDismiss: () -> Unit,
    anchorBounds: Rect,
    items: List<MenuEntry>,
) {
    val shown = remember { Animatable(0f) }
    var mounted by remember { mutableStateOf(false) }
    LaunchedEffect(open) {
        if (open) {
            mounted = true
            shown.animateTo(1f, Motion.layout())
        } else {
            shown.animateTo(0f, Motion.fade())
            mounted = false
        }
    }
    if (!mounted) return
    BackHandler(open, onDismiss)
    Overlay {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss),
            )
            val density = LocalDensity.current
            val screenW = with(density) { maxWidth.toPx() }
            val screenH = with(density) { maxHeight.toPx() }
            SimpleMenuPlacement(anchorBounds, screenW, screenH, shown.value) {
                MenuPanel(items, onDismiss)
            }
        }
    }
}

@Composable
private fun SimpleMenuPlacement(
    anchor: Rect,
    screenW: Float,
    screenH: Float,
    shown: Float,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val margin = with(density) { 12.dp.toPx() }
    val gap = with(density) { 6.dp.toPx() }
    var origin by remember { mutableStateOf(TransformOrigin(0.5f, 0f)) }
    val growUp = anchor.center.y > screenH * 0.4f
    val alignStart = anchor.center.x < screenW * 0.5f
    Box(
        Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
            val x = if (alignStart) {
                anchor.left
            } else {
                anchor.right - placeable.width
            }.coerceIn(margin, (screenW - margin - placeable.width).coerceAtLeast(margin))
            val y = if (growUp) {
                anchor.top - gap - placeable.height
            } else {
                anchor.bottom + gap
            }.coerceIn(margin, (screenH - margin - placeable.height).coerceAtLeast(margin))
            origin = TransformOrigin(
                ((anchor.center.x - x) / placeable.width.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f),
                if (growUp) 1f else 0f,
            )
            layout(constraints.maxWidth, constraints.maxHeight) {
                placeable.place(x.roundToInt(), y.roundToInt())
            }
        }.graphicsLayer {
            alpha = shown.coerceIn(0f, 1f)
            val scale = 0.55f + 0.45f * shown
            scaleX = scale
            scaleY = scale
            transformOrigin = origin
        },
    ) {
        content()
    }
}

@Composable
fun AnchoredInfoTooltip(
    open: Boolean,
    onDismiss: () -> Unit,
    anchorBounds: Rect,
    what: String,
    cost: String,
) {
    val shown = remember { Animatable(0f) }
    var mounted by remember { mutableStateOf(false) }
    LaunchedEffect(open) {
        if (open) {
            mounted = true
            shown.animateTo(1f, Motion.layout())
        } else {
            shown.animateTo(0f, Motion.fade())
            mounted = false
        }
    }
    if (!mounted) return
    BackHandler(open, onDismiss)
    Overlay {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss),
            )
            val density = LocalDensity.current
            val screenW = with(density) { maxWidth.toPx() }
            val screenH = with(density) { maxHeight.toPx() }
            SimpleMenuPlacement(anchorBounds, screenW, screenH, shown.value) {
                val shape = RoundedRectangle(22.dp)
                Column(
                    Modifier
                        .widthIn(min = 220.dp, max = 280.dp)
                        .glass(shape, GlassKind.Menu)
                        .clip(shape)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AppText(what, style = Type.menuItem, maxLines = 5)
                    AppText(cost, style = Type.caption, color = Tokens.current.muted, maxLines = 5)
                }
            }
        }
    }
}

@Composable
private fun MenuPanel(items: List<MenuEntry>, close: () -> Unit) {
    val shape = RoundedRectangle(22.dp)
    Column(
        Modifier
            .widthIn(min = 220.dp, max = 280.dp)
            .glass(shape, GlassKind.Menu)
            .clip(shape)
            .padding(6.dp),
    ) {
        for (item in items) {
            MenuRow(item) {
                close()
                item.action()
            }
        }
    }
}

@Composable
private fun MenuRow(item: MenuEntry, tap: () -> Unit) {
    val ink = when {
        !item.enabled -> Tokens.current.foreground.copy(alpha = 0.3f)
        item.destructive -> Tokens.current.remove
        else -> Tokens.current.foreground
    }
    val (source, pressed) = rememberPress()
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 42.dp)
            .clip(RoundedRectangle(14.dp))
            .background(if (pressed) Tokens.current.foreground.copy(alpha = 0.08f) else Color.Transparent)
            .clickable(source, null, enabled = item.enabled, role = Role.Button, onClick = tap)
            .semantics { selected = item.checked }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(19.dp), contentAlignment = Alignment.Center) {
            if (item.icon != null) SimpleIcon(item.icon, size = 19.dp, tint = ink)
            else if (item.checked) SimpleIcon(Icons.Check, size = 19.dp, tint = ink)
        }
        AppText(item.label, Modifier.weight(1f), style = Type.menuItem, color = ink, maxLines = 2)
        if (item.detail.isNotEmpty()) {
            AppText(item.detail, style = Type.caption, color = Tokens.current.muted, maxLines = 1)
        }
    }
}
