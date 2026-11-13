package sh.simple.android.ui.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalThemesTest {
    @Test
    fun everyThemeDefinesSixteenAnsiColors() {
        val ids = listOf("match") + TerminalThemes.catalog.map { it.id }
        for (id in ids) {
            val palette = TerminalThemes.resolve(id, appDark = true)
            assertEquals(16, palette.ansi.size)
            palette.ansi.forEach { hex ->
                assertTrue(hex.startsWith("#"))
                assertEquals(7, hex.length)
            }
            assertTrue(palette.foreground.startsWith("#"))
            assertTrue(palette.background.startsWith("#"))
            assertTrue(palette.cursor.startsWith("#"))
        }
        val lightMatch = TerminalThemes.resolve("match", appDark = false)
        assertEquals(TerminalThemes.simpleLight.id, lightMatch.id)
    }
}
