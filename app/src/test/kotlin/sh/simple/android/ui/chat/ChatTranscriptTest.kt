package sh.simple.android.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test
import sh.simple.android.model.WireJson
import sh.simple.android.store.MessageEntity

class ChatTranscriptTest {
    @Test
    fun decodeStoredMessagesPreservesOrder() {
        val first = MessageEntity("h", "a", "m1", "user", 1, """{"id":"m1","role":"user","text":"one","ts":1}""")
        val second = MessageEntity("h", "a", "m2", "assistant", 2, """{"id":"m2","role":"assistant","text":"two","ts":2}""")
        val decoded = decodeStoredMessages(listOf(first, second))
        assertEquals(2, decoded.size)
        assertEquals("one", decoded[0].text)
        assertEquals("two", decoded[1].text)
        WireJson.decodeFromString<sh.simple.android.model.Message>(first.json)
    }

    @Test
    fun staleCachedIdsDropsRowsFromAnOlderChat() {
        assertEquals(listOf("old1"), staleCachedIds(listOf("old1", "keep"), setOf("keep", "new1")))
    }
}
