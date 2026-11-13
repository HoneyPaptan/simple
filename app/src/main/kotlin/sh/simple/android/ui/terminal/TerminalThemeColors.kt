package sh.simple.android.ui.terminal

import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TextStyle

fun TerminalPalette.toComposeBackground(): Color =
    Color(AndroidColor.parseColor(background))

fun TerminalPalette.applyTo(emulator: TerminalEmulator) {
    val colors = emulator.mColors
    for (i in 0 until 16) {
        colors.tryParseColor(i, ansi[i])
    }
    colors.tryParseColor(TextStyle.COLOR_INDEX_FOREGROUND, foreground)
    colors.tryParseColor(TextStyle.COLOR_INDEX_BACKGROUND, background)
    colors.tryParseColor(TextStyle.COLOR_INDEX_CURSOR, cursor)
}
