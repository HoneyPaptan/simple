package sh.simple.android.ui.welcome

import org.junit.Assert.assertEquals
import org.junit.Test

class WelcomeTopGapTest {
    @Test
    fun smallScreensKeepTheMinimumGap() {
        assertEquals(24, welcomeTopGap(592))
        assertEquals(24, welcomeTopGap(640))
    }

    @Test
    fun tallScreensCapTheGap() {
        assertEquals(150, welcomeTopGap(1000))
    }

    @Test
    fun midScreensCentreTheContent() {
        assertEquals(80, welcomeTopGap(800))
    }
}
