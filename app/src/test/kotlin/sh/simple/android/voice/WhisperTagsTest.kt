package sh.simple.android.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class WhisperTagsTest {
    @Test
    fun dropsWhisperNoiseTags() {
        assertEquals("", stripWhisperTags(" [BLANK_AUDIO]"))
        assertEquals("", stripWhisperTags("(silence) [MUSIC] *wind blowing*"))
        assertEquals("fix the failing test", stripWhisperTags("[BLANK_AUDIO] fix the failing test"))
        assertEquals("run it (twice) please", stripWhisperTags("run it (twice) please"))
    }
}
