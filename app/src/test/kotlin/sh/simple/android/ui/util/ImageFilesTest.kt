package sh.simple.android.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageFilesTest {
    @Test
    fun sampleSizeKeepsEdgeAtOrAboveTarget() {
        assertEquals(1, sampleSizeFor(2000, 1500, 2048))
        assertEquals(1, sampleSizeFor(4000, 3000, 2048))
        assertEquals(2, sampleSizeFor(4096, 3072, 2048))
        assertEquals(4, sampleSizeFor(12000, 9000, 2048))
        assertEquals(8, sampleSizeFor(4000, 3000, 256))
    }
}
