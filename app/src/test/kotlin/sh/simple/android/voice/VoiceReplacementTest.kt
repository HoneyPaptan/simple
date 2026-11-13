package sh.simple.android.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceReplacementTest {
    @Test
    fun wholeWordCaseInsensitive() {
        val rules = listOf(VoiceReplacement("1", "get hub", "GitHub"))
        assertEquals("Try GitHub today", applyVoiceReplacements("Try get hub today", rules))
        assertEquals("Try GitHub today", applyVoiceReplacements("Try GET HUB today", rules))
        assertEquals("Try gethub today", applyVoiceReplacements("Try gethub today", rules))
    }

    @Test
    fun multipleRulesInOrder() {
        val rules = listOf(
            VoiceReplacement("1", "simple", "Simple"),
            VoiceReplacement("2", "d", "daemon"),
        )
        assertEquals("Simple daemon", applyVoiceReplacements("simple d", rules))
    }

    @Test
    fun emptyHeardSkipped() {
        val rules = listOf(VoiceReplacement("1", "  ", "x"))
        assertEquals("hello", applyVoiceReplacements("hello", rules))
    }
}
