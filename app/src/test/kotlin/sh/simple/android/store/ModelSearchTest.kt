package sh.simple.android.store

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sh.simple.android.model.ModelInfo

class ModelSearchTest {
    @Test
    fun searchMatchesProviderLabel() {
        val info = ModelInfo("opencode/big-pickle", "Big Pickle")
        assertTrue(modelSearchMatches(info, "zen"))
        assertTrue(modelSearchMatches(info, "Big"))
        assertFalse(modelSearchMatches(info, "anthropic"))
    }
}
