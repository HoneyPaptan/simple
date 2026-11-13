package sh.simple.android.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePalettesTest {
    @Test
    fun everyThemeReturnsBothModes() {
        for (theme in ThemeId.entries) {
            val dark = AppPalettes.palette(theme, dark = true)
            val light = AppPalettes.palette(theme, dark = false)
            assertTrue(dark.background != Color.Unspecified)
            assertTrue(light.background != Color.Unspecified)
        }
    }

    @Test
    fun darkOnlyThemesUseDarkPaletteInLightMode() {
        for (theme in listOf(ThemeId.OneDarkPro, ThemeId.Cobalt2)) {
            assertEquals(
                AppPalettes.palette(theme, dark = true),
                AppPalettes.palette(theme, dark = false),
            )
        }
    }

    @Test
    fun unknownStoredThemeFallsBack() {
        assertEquals(ThemeId.Simple, ThemeId.fromStored("not_a_theme"))
        assertEquals(ThemeId.Simple, ThemeId.fromStored(null))
    }

    @Test
    fun everyPalettePassesContrast() {
        for (theme in ThemeId.entries) {
            for (dark in listOf(false, true)) {
                val palette = AppPalettes.palette(theme, dark)
                val fgBg = contrastRatio(palette.foreground, palette.background)
                val fgCard = contrastRatio(palette.foreground, palette.card)
                val mutedBg = contrastRatio(palette.muted, palette.background)
                assertTrue("$theme dark=$dark fg/background $fgBg", fgBg >= 7.0)
                assertTrue("$theme dark=$dark fg/card $fgCard", fgCard >= 7.0)
                assertTrue("$theme dark=$dark muted/background $mutedBg", mutedBg >= 4.5)
            }
        }
    }

    private fun contrastRatio(foreground: Color, background: Color): Double {
        val l1 = foreground.luminance()
        val l2 = background.luminance()
        val lighter = maxOf(l1, l2)
        val darker = minOf(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }
}
