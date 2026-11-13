package sh.simple.android.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import sh.simple.android.store.MessageEntity
import sh.simple.android.store.MessageImageEntity

class TodayTilePreviewTest {
    @Test
    fun tileImageIgnoresUserAttachments() {
        val messages = listOf(
            MessageEntity("h", "a", "m1", "assistant", 1, """{"id":"m1","role":"assistant","text":"old","ts":1}"""),
            MessageEntity("h", "a", "m2", "user", 2, """{"id":"m2","role":"user","text":"pic","ts":2}"""),
        )
        val stored = listOf(
            MessageImageEntity("h", "a", "m2", 1, "/tmp/a.jpg"),
        )
        assertNull(tilePreviewImagePath(messages, stored))
    }

    @Test
    fun tileImageComesFromTheLatestAgentReply() {
        val messages = listOf(
            MessageEntity("h", "a", "m1", "assistant", 1, """{"id":"m1","role":"assistant","text":"chart","ts":1}"""),
            MessageEntity("h", "a", "m2", "user", 2, """{"id":"m2","role":"user","text":"pic","ts":2}"""),
        )
        val stored = listOf(
            MessageImageEntity("h", "a", "m1", 0, "/tmp/reply.jpg"),
            MessageImageEntity("h", "a", "m2", 1, "/tmp/attachment.jpg"),
        )
        assertEquals("/tmp/reply.jpg", tilePreviewImagePath(messages, stored))
    }

    @Test
    fun tileImageSkipsMessagesWithoutFiles() {
        val messages = listOf(
            MessageEntity("h", "a", "m1", "assistant", 1, """{"id":"m1","role":"assistant","text":"words","ts":1}"""),
        )
        assertNull(tilePreviewImagePath(messages, emptyList()))
    }

    @Test
    fun previewDropsMarkdownButKeepsSnakeCase() {
        assertEquals(
            "Status: overlap cleared in my_file.kt see docs",
            plainPreview("**Status:** overlap cleared in `my_file.kt`\n- see [docs](http://x)"),
        )
    }
}
