package sh.simple.android.ui.pane

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sh.simple.android.model.Pane
import sh.simple.android.store.AgentEntity

class PaneTargetsTest {
    private fun shellPane(id: String = "t1") = Pane(
        id = id,
        runtime = "hc",
        workspace = "/w",
        title = "fish",
        kind = "shell",
        status = "idle",
    )

    private fun agentPane(id: String = "h:w1:p9", kind: String = "claude") = Pane(
        id = id,
        runtime = "herdr",
        workspace = "/w",
        title = "Brain worker",
        kind = kind,
        status = "working",
    )

    @Test
    fun paneHostsAgentExcludesShellAndOther() {
        assertFalse(paneHostsAgent(shellPane()))
        assertFalse(paneHostsAgent(shellPane().copy(kind = "other")))
        assertTrue(paneHostsAgent(agentPane()))
    }

    @Test
    fun resolveChatIdMatchesAgentTableOrHerdrPane() {
        val hostAgents = listOf(
            AgentEntity("h", "h:w1:p9", "claude", "T", "/w", "/w", "working", null, null, null, 0, false, false, "{}"),
        )
        assertEquals("h:w1:p9", resolvePaneChatAgentId(agentPane(), hostAgents))
        assertEquals("h:w1:p9", resolvePaneChatAgentId(agentPane(id = "w1:p9"), hostAgents))
    }

    @Test
    fun resolveChatFallsBackToPaneId() {
        assertEquals("t2", resolvePaneChatAgentId(agentPane(id = "t2"), emptyList()))
    }
}
