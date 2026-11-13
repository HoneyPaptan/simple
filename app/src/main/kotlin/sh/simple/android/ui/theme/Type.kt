package sh.simple.android.ui.theme

import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import sh.simple.android.R

object Type {
    val largeTitle = style(22f, 28f, FontWeight.Medium)
    val headline = style(17f, 22f, FontWeight.SemiBold)
    val body = style(17f, 24f)
    val chatBody = style(17f, 24f, lineScale = 1.42f)
    val chatHeading = style(22f, 28f, FontWeight.SemiBold)
    val rowLabel = style(17f, 22f)
    val meta = style(15f, 20f)
    val section = style(15f, 20f)
    val menuItem = style(16f, 21f)
    val chip = style(15f, 20f)
    val code = style(12.5f, 18f, mono = true).let {
        it.copy(platformStyle = PlatformTextStyle(includeFontPadding = false))
    }
    val toolLine = style(16f, 21f)
    val caption = style(13f, 18f)
}

val TextStyle.medium get() = copy(fontWeight = FontWeight.Medium)
val TextStyle.semibold get() = copy(fontWeight = FontWeight.SemiBold)

private val geist = FontFamily(Font(R.font.geist))
private val geistMono = FontFamily(Font(R.font.geist_mono))

private fun style(
    size: Float,
    line: Float,
    weight: FontWeight = FontWeight.Normal,
    lineScale: Float = 1f,
    mono: Boolean = false,
) = TextStyle(
    fontFamily = if (mono) geistMono else geist,
    fontSize = size.sp,
    lineHeight = (size * lineScale).sp,
    fontWeight = weight,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    fontFeatureSettings = if (mono) "liga 0, calt 0, dlig 0" else "calt 0",
)
