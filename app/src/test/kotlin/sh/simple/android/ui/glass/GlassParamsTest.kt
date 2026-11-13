package sh.simple.android.ui.glass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sh.simple.android.ui.theme.AppearanceSettings
import sh.simple.android.ui.theme.SimpleColors

class GlassParamsTest {
  private val colors = SimpleColors.Dark

  @Test
  fun liquidOffUsesOpaqueSurfaces() {
    val settings = AppearanceSettings(liquidGlass = false, blur = 40, tint = 20)
    val pill = glassSurfaceParams(GlassKind.Pill, settings, liquidActive = false, colors)
    val sheet = glassSurfaceParams(GlassKind.Sheet, settings, liquidActive = false, colors)
    assertEquals(colors.raised, pill.surfaceColor)
    assertEquals(colors.sheetFillOpaque, sheet.surfaceColor)
    assertFalse(pill.useLiquidBackdrop)
  }

  @Test
  fun blurScalesWithSetting() {
    val low = AppearanceSettings(blur = 0)
    val high = AppearanceSettings(blur = 40)
    val lowRadius = glassBlurRadiusDp(GlassKind.Circle, low)
    val highRadius = glassBlurRadiusDp(GlassKind.Circle, high)
    assertTrue(highRadius > lowRadius)
    assertEquals(0f, lowRadius.value, 0.01f)
  }

  @Test
  fun tintScalesFillAlpha() {
    val heavy = AppearanceSettings(tint = 100, liquidGlass = true)
    val light = AppearanceSettings(tint = 25, liquidGlass = true)
    val heavyColor = glassSurfaceColor(GlassKind.Card, heavy, liquidActive = true, colors)
    val lightColor = glassSurfaceColor(GlassKind.Card, light, liquidActive = true, colors)
    assertTrue(heavyColor.alpha > lightColor.alpha)
  }

  @Test
  fun liquidGateRequiresCrossWindowBlur() {
    val settings = AppearanceSettings(liquidGlass = true)
    assertFalse(glassLiquidEnabled(settings, sdkSupportsBlur = true, crossWindowBlur = false))
    assertTrue(glassLiquidEnabled(settings, sdkSupportsBlur = true, crossWindowBlur = true))
    assertFalse(glassLiquidEnabled(settings, sdkSupportsBlur = false, crossWindowBlur = true))
  }
}
