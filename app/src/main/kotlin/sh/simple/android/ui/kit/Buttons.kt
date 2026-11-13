package sh.simple.android.ui.kit

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kyant.shapes.Capsule
import sh.simple.android.ui.glass.rememberPress
import sh.simple.android.ui.theme.Tokens
import sh.simple.android.ui.theme.Type
import sh.simple.android.ui.theme.medium

@Composable
fun Modifier.pressScale(pressed: Boolean): Modifier {
    val scale by animateFloatAsState(if (pressed) Motion.PRESS_SCALE else 1f, Motion.press(), label = "Press")
    return graphicsLayer { scaleX = scale; scaleY = scale }
}

@Composable
fun PrimaryButton(title: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val (source, pressed) = rememberPress()
    val fill by animateColorAsState(
        if (enabled) Tokens.current.primaryFill else Tokens.current.primaryFill.copy(alpha = 0.28f),
        Motion.hover(),
        label = "PrimaryFill",
    )
    val ink by animateColorAsState(
        if (enabled) Tokens.current.onPrimary else Tokens.current.onPrimary.copy(alpha = 0.4f),
        Motion.hover(),
        label = "PrimaryInk",
    )
    Box(
        modifier
            .pressScale(if (enabled) pressed else false)
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .clip(Capsule())
            .background(fill)
            .clickable(source, null, enabled = enabled, role = Role.Button, onClick = onClick)
            .focusRing(Capsule())
            .padding(horizontal = 18.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        AppText(title, style = Type.rowLabel.medium, color = ink, maxLines = 1)
    }
}

@Composable
fun SecondaryButton(
    title: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    maxLines: Int = 1,
    onClick: () -> Unit,
) {
    val (source, pressed) = rememberPress()
    Box(
        modifier
            .pressScale(pressed)
            .heightIn(min = MinTouchTarget)
            .clip(Capsule())
            .background(Tokens.current.raised)
            .clickable(source, null, enabled = enabled, role = Role.Button, onClick = onClick)
            .focusRing(Capsule())
            .padding(horizontal = 18.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        AppText(title, style = Type.rowLabel.medium, maxLines = maxLines)
    }
}

@Composable
fun Toggle(
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: @Composable RowScope.() -> Unit,
) {
    val offTrack = Tokens.current.foreground.copy(alpha = 0.18f)
    val track by animateColorAsState(if (checked) Tokens.current.foreground else offTrack, Motion.hover(), label = "Track")
    val knob by animateDpAsState(if (checked) 14.dp else 0.dp, Motion.hover(), label = "Knob")
    val knobInk = if (checked) Tokens.current.onPrimary else Tokens.current.background
    Row(
        modifier
            .heightIn(min = MinTouchTarget)
            .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) { label() }
        Box(Modifier.size(34.dp, 20.dp).clip(Capsule()).background(track).padding(2.dp)) {
            Box(Modifier.offset(x = knob).size(16.dp).clip(Capsule()).background(knobInk))
        }
    }
}

@Composable
fun <T> Segmented(
    selection: T,
    options: List<Pair<T, String>>,
    select: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.45f)
            .clip(Capsule())
            .background(Tokens.current.raised)
            .padding(3.dp),
    ) {
        val index = options.indexOfFirst { it.first == selection }.coerceAtLeast(0)
        val segment = (maxWidth - 2.dp * (options.size - 1)) / options.size
        val thumb by animateDpAsState((segment + 2.dp) * index, Motion.morph(), label = "Thumb")
        Box(Modifier.offset(x = thumb).size(width = segment, height = MinTouchTarget).clip(Capsule()).background(Tokens.current.background))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for ((id, label) in options) {
                val on = id == selection
                Box(
                    Modifier
                        .size(width = segment, height = MinTouchTarget)
                        .clip(Capsule())
                        .selectable(on, role = Role.Tab, enabled = enabled) { if (enabled) select(id) },
                    contentAlignment = Alignment.Center,
                ) {
                    AppText(label, style = Type.meta.medium, color = if (on) Tokens.current.foreground else Tokens.current.muted, maxLines = 1)
                }
            }
        }
    }
}
