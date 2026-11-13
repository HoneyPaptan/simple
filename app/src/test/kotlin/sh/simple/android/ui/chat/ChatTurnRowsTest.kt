package sh.simple.android.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatTurnRowsTest {
    private val blocks = listOf(
        ChatBlock.User("u1", "fix it"),
        ChatBlock.Thinking("t1", "hmm"),
        ChatBlock.Assistant("a1", "done"),
        ChatBlock.User("u2", "more"),
        ChatBlock.Assistant("a2", "working on it"),
    )

    @Test
    fun finishedTurnCollapsesWorkIntoOnePill() {
        val turns = splitChatTurns(blocks, working = true)
        val rows = chatTurnRows(turns, emptyMap(), emptySet())
        assertEquals(
            listOf("u1", "u1:header", "u1:worked", "a1", "u2", "u2:header", "a2"),
            rows.map { it.key },
        )
        assertEquals(TurnGapDp, rows[4].topGap)
    }

    @Test
    fun openTurnShowsItsWorkRowsAndKeysStayUnique() {
        val turns = splitChatTurns(blocks, working = false)
        val rows = chatTurnRows(turns, emptyMap(), setOf("u1"))
        assertEquals(listOf("u1", "u1:header", "u1:worked", "t1", "a1"), rows.take(5).map { it.key })
        assertEquals(rows.size, rows.map { it.key }.toSet().size)
    }
}
