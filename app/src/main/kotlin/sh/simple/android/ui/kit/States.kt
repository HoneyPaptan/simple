package sh.simple.android.ui.kit

import androidx.compose.ui.graphics.CompositingStrategy
import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import sh.simple.android.ui.theme.Tokens
import sh.simple.android.ui.theme.Type

private val LocalLoadingShimmer = staticCompositionLocalOf<Float?> { null }

private const val ShimmerMs = 1600

private fun Color.shimmerBandPeak(): Color {
    val lift = 0.35f
    return Color(
        red = red + (1f - red) * lift,
        green = green + (1f - green) * lift,
        blue = blue + (1f - blue) * lift,
        alpha = alpha,
    )
}

@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

@Composable
fun rememberLoadingShimmerShift(): Float {
    val reduceMotion = rememberReduceMotion()
    if (reduceMotion) return 0f
    val transition = rememberInfiniteTransition("loadShimmer")
    val shift by transition.animateFloat(
        0f,
        1f,
        infiniteRepeatable(tween(ShimmerMs, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "shimmer",
    )
    return shift
}

@Composable
fun LoadingShimmerContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shift = rememberLoadingShimmerShift()
    CompositionLocalProvider(LocalLoadingShimmer provides shift) {
        Box(modifier) { content() }
    }
}

@Composable
fun ShimmerText(
    text: String,
    style: TextStyle,
    baseColor: Color,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberReduceMotion()
    if (reduceMotion) {
        AppText(text, modifier, style = style, color = baseColor, maxLines = 1)
        return
    }
    val transition = rememberInfiniteTransition("shimmerText")
    val shift by transition.animateFloat(
        0f,
        1f,
        infiniteRepeatable(tween(ShimmerMs, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "shimmerTextShift",
    )
    val band = baseColor.shimmerBandPeak()
    BasicText(
        text = text,
        modifier = modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithCache {
            val bandWidth = size.width * 0.45f
            onDrawWithContent {
                drawContent()
                val travel = size.width + bandWidth
                val startX = shift * travel - bandWidth
                drawRect(
                    brush = Brush.linearGradient(
                        0f to Color.Transparent,
                        0.5f to band,
                        1f to Color.Transparent,
                        start = Offset(startX, 0f),
                        end = Offset(startX + bandWidth, size.height),
                    ),
                    blendMode = BlendMode.SrcAtop,
                )
            }
        },
        style = style.copy(color = baseColor),
        maxLines = 1,
    )
}

@Composable
fun LoadingRow(
    lines: Int = 3,
    modifier: Modifier = Modifier,
    shimmerShift: Float? = null,
) {
    val reduceMotion = rememberReduceMotion()
    val shared = LocalLoadingShimmer.current
    val shift = when {
        shimmerShift != null -> shimmerShift
        shared != null -> shared
        reduceMotion -> 0f
        else -> rememberLoadingShimmerShift()
    }
    val peak = Tokens.current.raised.shimmerBandPeak()
    Column(
        modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        repeat(lines.coerceIn(1, 6)) { index ->
            val width = when (index) {
                0 -> 0.72f
                1 -> 0.55f
                else -> 0.4f
            }
            Box(
                Modifier
                    .fillMaxWidth(width)
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Tokens.current.raised)
                    .then(
                        if (!reduceMotion) {
                            Modifier.drawWithCache {
                                onDrawWithContent {
                                    drawContent()
                                    val bandWidth = size.width * 0.55f
                                    val travel = size.width + bandWidth
                                    val startX = shift * travel - bandWidth
                                    drawRect(
                                        brush = Brush.linearGradient(
                                            0f to Color.Transparent,
                                            0.5f to peak,
                                            1f to Color.Transparent,
                                            start = Offset(startX, 0f),
                                            end = Offset(startX + bandWidth, size.height),
                                        ),
                                        blendMode = BlendMode.SrcAtop,
                                    )
                                }
                            }
                        } else {
                            Modifier
                        },
                    ),
            )
        }
    }
}

fun elapsedLabel(seconds: Int): String = when {
    seconds < 60 -> "${seconds}s"
    seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
    else -> "${seconds / 3600}h ${seconds % 3600 / 60}m"
}

@Composable
fun WorkingIndicator(
    toolName: String?,
    workingSinceMs: Long,
    modifier: Modifier = Modifier,
    detail: String = "",
) {
    var elapsed by remember { mutableIntStateOf(0) }
    LaunchedEffect(workingSinceMs) {
        while (true) {
            val base = ((System.currentTimeMillis() - workingSinceMs) / 1000).toInt().coerceAtLeast(0)
            elapsed = base
            delay(1000)
        }
    }
    val tool = toolName?.trim().orEmpty()
    val shimmer = when {
        tool.isNotEmpty() -> tool
        detail == "Sending" -> "Sending"
        detail == "Thinking" || detail == "Starting" -> "Thinking"
        else -> "Working"
    }
    val pillShape = RoundedCornerShape(999.dp)
    Row(
        modifier
            .padding(vertical = 8.dp)
            .clip(pillShape)
            .background(Tokens.current.card)
            .border(0.5.dp, Tokens.current.hairline, pillShape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DotGridLoader(Tokens.current.foreground, modifier = Modifier.padding(end = 8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ShimmerText(shimmer, style = Type.toolLine, baseColor = Tokens.current.foreground)
            if (elapsed >= 3) {
                AppText(
                    " · ${elapsedLabel(elapsed)}",
                    style = Type.toolLine,
                    color = Tokens.current.muted,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    help: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SimpleIcon(icon, size = 28.dp, tint = Tokens.current.muted)
        AppText(title, Modifier.padding(top = 12.dp), style = Type.headline, textAlign = TextAlign.Center)
        AppText(help, Modifier.padding(top = 6.dp), style = Type.meta, color = Tokens.current.muted, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            PrimaryButton(actionLabel, Modifier.padding(top = 16.dp), onClick = onAction)
        }
    }
}

@Composable
fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppText(message, style = Type.body, color = Tokens.current.foreground, textAlign = TextAlign.Center)
        if (onRetry != null) {
            SecondaryButton("Retry", Modifier.padding(top = 12.dp), onClick = onRetry)
        }
    }
}
