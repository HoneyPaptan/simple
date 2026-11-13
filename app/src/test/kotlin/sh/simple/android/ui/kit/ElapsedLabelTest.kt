package sh.simple.android.ui.kit

import org.junit.Assert.assertEquals
import org.junit.Test

class ElapsedLabelTest {
    @Test
    fun formatsSecondsMinutesAndHours() {
        assertEquals("14s", elapsedLabel(14))
        assertEquals("3m 12s", elapsedLabel(192))
        assertEquals("2h 5m", elapsedLabel(7500))
    }
}
