package sh.simple.android.ui.glass

import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalView
import java.util.function.Consumer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import com.kyant.shapes.RoundedRectangularShape
import kotlin.math.min
import sh.simple.android.ui.kit.Motion
import sh.simple.android.ui.theme.AppearanceSettings
import sh.simple.android.ui.theme.LocalAppearance
import sh.simple.android.ui.theme.SimpleColors
import sh.simple.android.ui.theme.Tokens

enum class GlassKind { Circle, Pill, Card, Sheet, Menu, Dialog }

enum class ScrollEdgeKind { Top, Bottom }

val LocalBackdrop = staticCompositionLocalOf<Backdrop> { emptyBackdrop() }

private const val REF_BLUR = 28f

private fun AppearanceSettings.useGlass(): Boolean =
    liquidGlass && Build.VERSION.SDK_INT >= 31

private fun appearanceGlassKey(settings: AppearanceSettings): String = buildString {
    append(settings.liquidGlass)
    append(':')
    append(settings.blur)
    append(':')
    append(settings.tint)
    append(':')
    append(settings.lensStrength)
    append(':')
    append(settings.depthEffect)
    append(':')
    append(settings.chromaticAberration)
    append(':')
    append(settings.brightness)
    append(':')
    append(settings.contrast)
    append(':')
    append(settings.saturation)
    append(':')
    append(settings.lightEdge)
    append(':')
    append(settings.glassShadow)
}

@Composable
private fun rememberCrossWindowBlurEnabled(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
    val view = LocalView.current
    val windowManager = view.context.getSystemService(WindowManager::class.java)
    var enabled by remember { mutableStateOf(windowManager.isCrossWindowBlurEnabled) }
    DisposableEffect(windowManager) {
        val listener = Consumer<Boolean> { enabled = it }
        windowManager.addCrossWindowBlurEnabledListener(view.context.mainExecutor, listener)
        onDispose { windowManager.removeCrossWindowBlurEnabledListener(listener) }
    }
    return enabled
}

@Composable
private fun glassBlurAvailable(settings: AppearanceSettings): Boolean {
    if (!settings.useGlass()) return false
    return rememberCrossWindowBlurEnabled()
}

@Composable
fun GlassStage(
    modifier: Modifier = Modifier,
    chrome: @Composable BoxScope.() -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    val ground = Tokens.current.background
    val settings = LocalAppearance.current
    val glassKey = appearanceGlassKey(settings)
    val onDraw: ContentDrawScope.() -> Unit = remember(ground, glassKey) {
        { drawRect(ground); drawContent() }
    }
    val backdrop = key(ground, glassKey) { rememberLayerBackdrop(onDraw = onDraw) }
    Box(modifier) {
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop), content = content)
        CompositionLocalProvider(LocalBackdrop provides backdrop) {
            Box(Modifier.fillMaxSize(), content = chrome)
        }
    }
}

@Composable
fun ScrollEdge(edge: ScrollEdgeKind, height: Dp, modifier: Modifier = Modifier) {
    val backdrop = LocalBackdrop.current
    val settings = LocalAppearance.current
    val colors = Tokens.current
    val top = edge == ScrollEdgeKind.Top
    if (!settings.useGlass()) {
        Box(
            modifier
                .fillMaxWidth()
                .height(height)
                .drawBehind {
                    val stops = arrayOf(
                        0f to colors.background.copy(alpha = 0.92f),
                        0.6f to colors.background.copy(alpha = 0.75f),
                        1f to Color.Transparent,
                    )
                    val brush = if (top) {
                        Brush.verticalGradient(*stops)
                    } else {
                        Brush.verticalGradient(*stops.map { (f, c) -> (1f - f) to c }.reversed().toTypedArray())
                    }
                    drawRect(brush)
                },
        )
        return
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    Brush.verticalGradient(
                        *if (top) {
                            arrayOf(0f to colors.scrollMask, 0.8f to colors.scrollMask, 1f to Color.Transparent)
                        } else {
                            arrayOf(0f to Color.Transparent, 0.2f to colors.scrollMask, 1f to colors.scrollMask)
                        },
                    ),
                    blendMode = BlendMode.DstIn,
                )
            }
            .drawPlainBackdrop(
                backdrop,
                shape = { RoundedRectangle(0.dp) },
                effects = { blur(10.dp.toPx() * settings.blur / REF_BLUR) },
                onDrawSurface = {
                    val wash = listOf(
                        colors.background.copy(alpha = 0.85f),
                        colors.background.copy(alpha = 0.45f),
                    )
                    drawRect(Brush.verticalGradient(if (top) wash else wash.reversed()))
                },
            ),
    )
}

@Composable
fun Modifier.glass(
    shape: RoundedRectangularShape = Capsule(),
    kind: GlassKind = GlassKind.Pill,
    pressed: Boolean = false,
    shadow: Boolean = true,
): Modifier {
    val settings = LocalAppearance.current
    val colors = Tokens.current
    val backdrop = LocalBackdrop.current
    val blurOk = glassBlurAvailable(settings)
    val params = glassSurfaceParams(kind, settings, blurOk, colors)
    TrackDrawBackdropSurface(params.useLiquidBackdrop)
    val surface = params.surfaceColor
    if (!blurOk) {
        val floating = kind == GlassKind.Menu || kind == GlassKind.Dialog
        val lifted = if (floating && shadow) shadow(12.dp, shape, ambientColor = colors.line, spotColor = colors.line) else this
        return lifted.clip(shape).graphicsLayer {
            val swell = if (pressed) Motion.PRESS_SCALE else 1f
            scaleX = swell
            scaleY = swell
        }.background(surface).border(0.5.dp, colors.line, shape)
    }
    val blurRadius = params.blurRadiusDp
    val lensScale = params.lensScale
    val swell by animateFloatAsState(if (pressed) 1.06f else 1f, Motion.layout(), label = "Glass")
    return graphicsLayer { scaleX = swell; scaleY = swell }.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            colorControls(
                brightness = settings.brightness,
                contrast = settings.contrast,
                saturation = settings.saturation,
            )
            if (blurRadius.value > 0f) blur(blurRadius.toPx())
            val rim = min(size.minDimension * 0.3f, 18.dp.toPx()) * lensScale
            if (Build.VERSION.SDK_INT >= 33 && rim > 0f) {
                lens(
                    rim,
                    rim * 2f,
                    depthEffect = settings.depthEffect,
                    chromaticAberration = settings.chromaticAberration,
                )
            }
        },
        highlight = if (!settings.lightEdge) {
            null
        } else {
            { Highlight(width = 0.75.dp, alpha = 0.45f) }
        },
        shadow = if (!shadow || !settings.glassShadow) {
            null
        } else {
            {
                Shadow(
                    radius = 18.dp,
                    offset = androidx.compose.ui.unit.DpOffset(0.dp, 4.dp),
                    color = colors.glassShadow,
                )
            }
        },
        onDrawSurface = { drawRect(surface) },
    )
}

@Composable
fun rememberPress(): Pair<MutableInteractionSource, Boolean> {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    return source to pressed
}
