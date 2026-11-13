package sh.simple.android.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class InlineMarkdownTest {
    @Test
    fun codeBoldItalicAndLinks() {
        val spans = parseInline("Run `simpled` with **care** and *calm*, see [docs](https://x.dev).")
        assertEquals(
            listOf(
                InlineSpan("Run "),
                InlineSpan("simpled", code = true),
                InlineSpan(" with "),
                InlineSpan("care", bold = true),
                InlineSpan(" and "),
                InlineSpan("calm", italic = true),
                InlineSpan(", see "),
                InlineSpan("docs", link = "https://x.dev"),
                InlineSpan("."),
            ),
            spans,
        )
    }

    @Test
    fun plainSymbolsStayText() {
        assertEquals(listOf(InlineSpan("a * b, snake_case_name, 2 ` 3")), parseInline("a * b, snake_case_name, 2 ` 3"))
    }

    @Test
    fun numberedListItems() {
        val blocks = parseMarkdown("1. First\n2. Second")
        assertEquals(listOf(MdBlock.ListItem("First", "1."), MdBlock.ListItem("Second", "2.")), blocks)
    }
}
