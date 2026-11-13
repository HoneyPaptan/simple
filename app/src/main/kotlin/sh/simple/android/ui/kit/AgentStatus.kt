package sh.simple.android.ui.kit

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import sh.simple.android.ui.theme.Tokens
import sh.simple.android.ui.theme.Type
import sh.simple.android.ui.util.AgentStatusLine
import sh.simple.android.ui.util.AgentStatusTone

@Composable
fun AgentStatusText(line: AgentStatusLine, modifier: Modifier = Modifier) {
    val color = toneColor(line.tone)
    if (line.shimmer) {
        Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            ShimmerText("Working", style = Type.meta, baseColor = color)
            if (line.workingTail.isNotEmpty()) {
                AppText(line.workingTail, style = Type.meta, color = color, maxLines = 1)
            }
        }
    } else {
        AppText(line.text, modifier, style = Type.meta, color = color, maxLines = 2)
    }
}

@Composable
fun toneColor(tone: AgentStatusTone): Color = when (tone) {
    AgentStatusTone.Muted -> Tokens.current.muted
    AgentStatusTone.Attention -> Tokens.current.attention
    AgentStatusTone.Remove -> Tokens.current.remove
}
