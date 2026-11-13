package sh.simple.android.ui.glass

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import sh.simple.android.ui.theme.AppearanceSettings
import sh.simple.android.ui.theme.SimpleColors

private const val REF_BLUR = 28f

data class GlassSurfaceParams(
    val blurRadiusDp: Dp,
    val surfaceColor: Color,
    val lensScale: Float,
    val useLiquidBackdrop: Boolean,
)

fun glassLiquidEnabled(settings: AppearanceSettings, sdkSupportsBlur: Boolean, crossWindowBlur: Boolean): Boolean =
    settings.liquidGlass && sdkSupportsBlur && crossWindowBlur

fun glassBlurRadiusDp(kind: GlassKind, settings: AppearanceSettings): Dp = when (kind) {
    GlassKind.Sheet -> 40.dp
    GlassKind.Menu, GlassKind.Dialog -> 36.dp
    else -> 28.dp
} * (settings.blur / REF_BLUR)

private fun Color.scaledFill(settings: AppearanceSettings): Color =
    copy(alpha = (alpha * settings.fillScale).coerceIn(0f, 1f))

fun glassSurfaceColor(
    kind: GlassKind,
    settings: AppearanceSettings,
    liquidActive: Boolean,
    colors: SimpleColors,
): Color {
    if (!liquidActive) {
        return when (kind) {
            GlassKind.Sheet -> colors.sheetFillOpaque
            GlassKind.Menu, GlassKind.Dialog -> colors.menuFillOpaque
            else -> colors.raised
        }
    }
    return when (kind) {
        GlassKind.Sheet -> colors.sheetFill
        GlassKind.Menu, GlassKind.Dialog -> colors.menuFill
        else -> colors.glassFill.scaledFill(settings)
    }
}

fun glassSurfaceParams(
    kind: GlassKind,
    settings: AppearanceSettings,
    liquidActive: Boolean,
    colors: SimpleColors,
): GlassSurfaceParams = GlassSurfaceParams(
    blurRadiusDp = glassBlurRadiusDp(kind, settings),
    surfaceColor = glassSurfaceColor(kind, settings, liquidActive, colors),
    lensScale = settings.lensStrength / 100f,
    useLiquidBackdrop = liquidActive,
)
