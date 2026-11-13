package sh.simple.android.ui.changes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiffLinesTest {
    @Test
    fun stripsGitHeaders() {
        val raw = """
            diff --git a/x b/x
            index 123..456 100644
            --- a/x
            +++ b/x
            @@ -1 +1 @@
             old
            +new
        """.trimIndent()
        val lines = parseDiffForDisplay(raw)
        assertTrue(lines.none { it.text.startsWith("diff --git") })
        assertEquals('@', lines.first().marker)
    }
}
