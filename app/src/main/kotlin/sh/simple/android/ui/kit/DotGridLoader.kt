package sh.simple.android.ui.kit

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sqrt

private const val CycleMs = 1700
private const val SwellEnd = 0.28f
private const val SinkEnd = 0.56f
private const val RestAlpha = 0.14f

fun dotGridWavePhase(row: Int, col: Int): Float {
    val dr = (row - 1).toFloat()
    val dc = (col - 1).toFloat()
    val dist = sqrt(dr * dr + dc * dc)
    return dist / sqrt(2f)
}

private fun dotGridPulse(local: Float): Pair<Float, Float> {
    val t = when {
        local < 0f -> 0f
        local < SwellEnd -> FastOutSlowInEasing.transform(local / SwellEnd)
        local < SinkEnd -> 1f - FastOutSlowInEasing.transform((local - SwellEnd) / (SinkEnd - SwellEnd))
        else -> 0f
    }
    val alpha = RestAlpha + (1f - RestAlpha) * t
    val scale = 1f + 0.18f * t
    return alpha to scale
}

@Composable
fun DotGridLoader(
    color: Color,
    size: Dp = 20.dp,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberReduceMotion()
    val density = LocalDensity.current
    val boxPx = with(density) { size.toPx() }
    val dotPx = with(density) { 3.dp.toPx() }
    val pitchPx = with(density) { 6.dp.toPx() }
    val origin = (boxPx - 2f * pitchPx) / 2f
    val cycleState = if (reduceMotion) {
        null
    } else {
        val transition = rememberInfiniteTransition("dotGrid")
        transition.animateFloat(
            0f,
            1f,
            infiniteRepeatable(tween(CycleMs), RepeatMode.Restart),
            label = "dotGridCycle",
        )
    }
    Canvas(modifier.size(size)) {
        val cycle = cycleState?.value ?: 0f
        for (row in 0..2) {
            for (col in 0..2) {
                val cx = origin + col * pitchPx
                val cy = origin + row * pitchPx
                val phase = dotGridWavePhase(row, col)
                val local = if (reduceMotion) {
                    if (row == 1 && col == 1) SwellEnd else -1f
                } else {
                    var p = cycle - phase * SwellEnd
                    if (p < 0f) p += 1f
                    p
                }
                val (alpha, scale) = dotGridPulse(local)
                val r = dotPx / 2f * scale
                drawCircle(color.copy(alpha = alpha), radius = r, center = Offset(cx, cy))
            }
        }
    }
}
