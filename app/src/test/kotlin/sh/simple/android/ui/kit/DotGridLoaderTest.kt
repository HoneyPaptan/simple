package sh.simple.android.ui.kit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DotGridLoaderTest {
    @Test
    fun centreLeadsCornersLast() {
        val centre = dotGridWavePhase(1, 1)
        val edge = dotGridWavePhase(1, 0)
        val corner = dotGridWavePhase(0, 0)
        assertEquals(0f, centre, 0.0001f)
        assertTrue(edge > centre)
        assertTrue(corner > edge)
        assertEquals(dotGridWavePhase(2, 2), corner, 0.0001f)
    }
}
