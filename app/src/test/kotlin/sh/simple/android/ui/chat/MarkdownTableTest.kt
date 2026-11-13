package sh.simple.android.ui.chat

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownTableTest {
    @Test
    fun pipeTableBecomesTableBlock() {
        val blocks = parseMarkdown("Contents:\n| Path | Type |\n|------|------|\n| `box` | file |\n| check | file |\nDone.")
        assertEquals(
            listOf(
                MdBlock.Paragraph("Contents:"),
                MdBlock.Table(listOf("Path", "Type"), listOf(listOf("`box`", "file"), listOf("check", "file"))),
                MdBlock.Paragraph("Done."),
            ),
            blocks,
        )
    }

    @Test
    fun lonePipeLineStaysParagraph() {
        assertEquals(listOf(MdBlock.Paragraph("| not a table |")), parseMarkdown("| not a table |"))
    }

    @Test
    fun threeColumnTableWithInlineCode() {
        val source = """
            | Key | Kind | Note |
            |-----|------|------|
            | `box` | file | `simpled` |
            | check | cmd | ok |
        """.trimIndent()
        val blocks = parseMarkdown(source)
        val table = blocks.filterIsInstance<MdBlock.Table>().single()
        assertEquals(listOf("Key", "Kind", "Note"), table.header)
        assertEquals(2, table.rows.size)
        assertEquals(listOf("`box`", "file", "`simpled`"), table.rows[0])
        assertEquals(40.dp, tableCellMinDp("short"))
        assertEquals(240.dp, tableCellMinDp("x".repeat(80)))
    }
}
