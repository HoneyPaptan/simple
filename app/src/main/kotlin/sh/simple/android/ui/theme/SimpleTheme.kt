package sh.simple.android.ui.theme

import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily

@Composable
fun SimpleTheme(
    appearance: AppearanceSettings = LocalAppearance.current,
    colors: SimpleColors = SimpleColors.Dark,
    codeFont: FontFamily = geistMonoFamily,
    content: @Composable () -> Unit,
) {
    val selection = remember(colors) {
        TextSelectionColors(
            handleColor = colors.foreground,
            backgroundColor = colors.foreground.copy(alpha = 0.25f),
        )
    }
    val codeStyle = remember(codeFont) {
        baseCodeStyle.copy(fontFamily = codeFont, fontFeatureSettings = "liga 0, calt 0, dlig 0")
    }
    CompositionLocalProvider(
        LocalAppearance provides appearance,
        LocalSimpleColors provides colors,
        LocalTextSelectionColors provides selection,
        LocalCodeStyle provides codeStyle,
        content = content,
    )
}
