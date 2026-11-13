package sh.simple.android.ui.composer

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceMicLevelTest {
    @Test
    fun ringStaysStillWithReducedMotion() {
        assertEquals(1f, voiceMicRingScale(0.9f, reduceMotion = true), 0.001f)
    }

    @Test
    fun ringScalesWithLevel() {
        assertEquals(1f, voiceMicRingScale(0f, reduceMotion = false), 0.001f)
        assertEquals(1.25f, voiceMicRingScale(1f, reduceMotion = false), 0.001f)
    }
}
