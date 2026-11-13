package sh.simple.android.store

import org.junit.Assert.assertEquals
import org.junit.Test
import sh.simple.android.model.ModelInfo

class ModelRowSubtitleTest {
    @Test
    fun providerFromIdAndEffortOnlyWhenSupported() {
        assertEquals("OpenCode Zen", modelRowSubtitle(ModelInfo("opencode/big-pickle", "Big Pickle"), "high"))
        assertEquals("OpenRouter · Max", modelRowSubtitle(ModelInfo("openrouter/x", "X", listOf("high", "max")), "max"))
        assertEquals("High", modelRowSubtitle(ModelInfo("opus", "Opus", listOf("high")), "high"))
        assertEquals("", modelRowSubtitle(ModelInfo("composer-2.5", "Composer 2.5"), "high"))
    }
}
