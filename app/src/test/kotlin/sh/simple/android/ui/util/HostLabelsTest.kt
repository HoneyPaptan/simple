package sh.simple.android.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HostLabelsTest {
    @Test
    fun showComputerOnlyWithMultipleHosts() {
        assertFalse(showPairedComputerNames(1))
        assertTrue(showPairedComputerNames(2))
    }

    @Test
    fun optionalComputerNameRespectsFlag() {
        assertNull(optionalComputerName("h1", mapOf("h1" to "box"), false))
        assertEquals("box", optionalComputerName("h1", mapOf("h1" to "box"), true))
    }
}
