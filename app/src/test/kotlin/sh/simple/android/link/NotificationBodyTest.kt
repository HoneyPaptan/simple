package sh.simple.android.link

import org.junit.Assert.assertEquals
import org.junit.Test
import sh.simple.android.model.Item

class NotificationBodyTest {
    @Test
    fun doneShowsReplyAndOthersShowTitle() {
        val done = Item("i1", "t1", "done", "Finished", body = "Created the file.\n\nAll tests pass.", created_ms = 1)
        assertEquals("Created the file. All tests pass.", notificationBody(done))
        assertEquals("Finished", notificationBody(done.copy(body = "  ")))
        assertEquals("Allow edit?", notificationBody(Item("i2", "t1", "prompt", "Allow edit?", body = "x", created_ms = 1)))
    }

    @Test
    fun doneSummaryDropsMarkdownMarks() {
        val done = Item("i1", "t1", "done", "Finished", body = "## Result\nThe first heading is **simple** (an `<h1>`).", created_ms = 1)
        assertEquals("Result The first heading is simple (an <h1>).", notificationBody(done))
    }

    @Test
    fun doneSummaryFlattensTables() {
        val done = Item("i1", "t1", "done", "Finished", body = "| Path | Type |\n|------|------|\n| `box` | file |", created_ms = 1)
        assertEquals("Path, Type box, file", notificationBody(done))
    }
}
