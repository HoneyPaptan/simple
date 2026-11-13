package sh.simple.android.ui.files

import org.junit.Assert.assertEquals
import org.junit.Test

class FileTextTest {
    @Test
    fun keepsTrailingNewlineWhenOriginalHadOne() {
        assertEquals("a\n", textForWrite("a\n", "a"))
        assertEquals("{\n}\n", textForWrite("{\n}\n", "{\n}"))
    }

    @Test
    fun omitsTrailingNewlineWhenOriginalHadNone() {
        assertEquals("a", textForWrite("a", "a"))
        assertEquals("a", textForWrite("a", "a\n"))
    }

    @Test
    fun viewerDropsOnlyTheFinalNewline() {
        assertEquals(listOf("a", "b"), viewerLines("a\nb\n"))
        assertEquals(listOf("a", ""), viewerLines("a\n\n"))
        assertEquals(listOf("a"), viewerLines("a"))
    }
}
