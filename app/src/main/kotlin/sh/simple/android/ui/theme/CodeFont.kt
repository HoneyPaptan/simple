package sh.simple.android.ui.theme

import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import sh.simple.android.R
import sh.simple.android.store.CodeFontStore
import androidx.compose.ui.unit.sp

val geistMonoFamily = FontFamily(Font(R.font.geist_mono))

internal val baseCodeStyle = TextStyle(
    fontFamily = geistMonoFamily,
    fontSize = 12.5.sp,
    lineHeight = 18.sp,
    fontWeight = FontWeight.Normal,
    platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
)

val LocalCodeStyle = staticCompositionLocalOf { baseCodeStyle }

@Composable
fun rememberCodeFontFamily(store: CodeFontStore): FontFamily {
    var family by remember { mutableStateOf(geistMonoFamily) }
    LaunchedEffect(store.choice, store.localFile.path) {
        if (store.choice.mode != "host" || !store.localFile.isFile) {
            family = geistMonoFamily
            return@LaunchedEffect
        }
        val tf = Typeface.createFromFile(store.localFile)
        if (tf == null) {
            store.noteLoadFailed()
            family = geistMonoFamily
        } else {
            family = FontFamily(tf)
        }
    }
    return family
}

@Composable
fun codeStyle(): TextStyle = LocalCodeStyle.current
