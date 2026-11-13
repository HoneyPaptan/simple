package sh.simple.android.ui.kit

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import sh.simple.android.ui.theme.Tokens
import sh.simple.android.ui.theme.Type

@Composable
fun SettingInfoTag(
    what: String,
    cost: String,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    Box(
        modifier
            .size(22.dp)
            .onGloballyPositioned { bounds = it.boundsInRoot() }
            .clip(CircleShape)
            .border(0.5.dp, Tokens.current.line, CircleShape)
            .clickable(remember { MutableInteractionSource() }, null) { open = true },
        contentAlignment = Alignment.Center,
    ) {
        AppText("i", style = Type.caption, color = Tokens.current.muted)
    }
    AnchoredInfoTooltip(open, { open = false }, bounds, what, cost)
}
