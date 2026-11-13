package sh.simple.android.ui.pane

import org.junit.Assert.assertEquals
import org.junit.Test

class PanePreviewTest {
    @Test
    fun previewKeepsLastNonBlankLinesAndTrimsNoise() {
        val raw = """
            OS: CachyOS
             · · · …
            ...
            8.5 GiB / 15.4 GiB
                uptime 17m
        """.trimIndent()
        assertEquals(
            "OS: CachyOS\n8.5 GiB / 15.4 GiB\nuptime 17m",
            formatPanePeekPreview(raw),
        )
    }

    @Test
    fun emptyPreviewIsBlank() {
        assertEquals("", formatPanePeekPreview("  \n· · ·\n…\n"))
    }
}
