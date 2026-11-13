package sh.simple.android.ui.pair

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PairErrorMessageTest {
    @Test
    fun daemonKindsMapToPlainSentences() {
        assertEquals("This code is wrong or has expired. Run simpled pair again for a new one.", pairErrorMessage("bad_code", "box"))
        assertEquals("This code was already used. Run simpled pair again for a new one.", pairErrorMessage("used", "box"))
        assertEquals(true, pairErrorMessage("unreachable", "box").startsWith("Can't reach box."))
        assertEquals(true, pairErrorMessage("cert_mismatch", "box").startsWith("box did not prove"))
    }

    @Test
    fun unknownKindsNeverLeakRawText() {
        for (kind in listOf("http_500", "bad_request", "weird_kind", "")) {
            val text = pairErrorMessage(kind, "box")
            assertFalse(text, text.contains(kind.ifEmpty { "\u0000" }))
            assertEquals("Pairing did not finish. Run simpled pair again and use the new code.", text)
        }
    }
}
