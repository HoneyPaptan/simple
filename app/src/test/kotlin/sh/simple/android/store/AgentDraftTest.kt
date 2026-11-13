package sh.simple.android.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import sh.simple.android.model.AgentModelsResult
import sh.simple.android.model.ModelInfo

class AgentDraftTest {
    @Test
    fun resetUsesCatalogDefaultsWhenKindChanges() {
        val codex = AgentModelsResult(
            models = listOf(
                ModelInfo("default", "Default"),
                ModelInfo("gpt-5-codex", "GPT-5 Codex"),
            ),
            modes = listOf("ask", "auto", "full"),
        )
        val reset = resetAgentDraftForCatalog(codex, "codex")
        assertEquals("default", reset.model)
        assertEquals("ask", reset.mode)
        assertFalse(reset.modeOptions.contains("plan"))
        assertEquals("Default", reset.modelLabel)
    }

    @Test
    fun resetKeepsComposerPickWhenCatalogHasIt() {
        val claude = AgentModelsResult(
            models = listOf(ModelInfo("default", "Default"), ModelInfo("opus", "Opus")),
            modes = listOf("ask", "plan", "auto", "full"),
        )
        val kept = resetAgentDraftForCatalog(claude, "claude", "opus", "plan")
        assertEquals("opus", kept.model)
        assertEquals("plan", kept.mode)
        val foreign = resetAgentDraftForCatalog(claude, "claude", "gpt-5-codex", "yolo")
        assertEquals("default", foreign.model)
        assertEquals("ask", foreign.mode)
    }

    @Test
    fun startModelRejectsForeignId() {
        val codex = AgentModelsResult(
            models = listOf(ModelInfo("default", "Default")),
            modes = listOf("ask"),
        )
        assertEquals("default", startModelForKind("codex", codex, "opus"))
        assertNull(startModelForKind("shell", codex, "opus"))
    }
}
