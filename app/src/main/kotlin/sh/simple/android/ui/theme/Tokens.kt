package sh.simple.android.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class SimpleColors(
    val background: Color,
    val foreground: Color,
    val muted: Color,
    val faint: Color,
    val card: Color,
    val raised: Color,
    val line: Color,
    val hairline: Color,
    val add: Color,
    val remove: Color,
    val attention: Color,
    val glassFill: Color,
    val glassEdgeHighlight: Color,
    val glassEdgeOutline: Color,
    val glassShadow: Color,
    val sheetFill: Color,
    val menuFill: Color,
    val dialogFill: Color,
    val menuFillOpaque: Color,
    val sheetFillOpaque: Color,
    val userBubble: Color,
    val userBubbleInk: Color,
    val onPrimary: Color,
    val primaryFill: Color,
    val dialogScrim: Color,
    val sheetScrim: Color,
    val scrollMask: Color,
    val codeBackground: Color,
    val diffAddBackground: Color,
    val diffRemoveBackground: Color,
    val sliderHandle: Color,
    val focusRing: Color,
    val brandSurface: Color,
    val brandAccent: Color,
    val iconInk: Color,
) {
    companion object {
        val iconVectorInk: Color get() = Color(0xFFFFFFFF)
        val Dark: SimpleColors get() = AppPalettes.palette(ThemeId.Simple, dark = true)
        val Light: SimpleColors get() = AppPalettes.palette(ThemeId.Simple, dark = false)
    }
}

val LocalSimpleColors = staticCompositionLocalOf { SimpleColors.Dark }

object Tokens {
    val current: SimpleColors
        @Composable
        @ReadOnlyComposable
        get() = LocalSimpleColors.current
}

object SimpleThemeColors {
    val colors: SimpleColors
        @Composable
        @ReadOnlyComposable
        get() = LocalSimpleColors.current
}
