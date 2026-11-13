package sh.simple.android.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import sh.simple.android.model.AgentContext

class AgentContextTest {
    @Test
    fun percentRoundsDown() {
        assertEquals(76, agentContextLeftPercent(AgentContext(used = 48000, window = 200_000)))
    }

    @Test
    fun percentNeverBelowZero() {
        assertEquals(0, agentContextLeftPercent(AgentContext(used = 300_000, window = 200_000)))
    }

    @Test
    fun unknownOmitsLabel() {
        assertNull(agentContextLeftLabel(null))
        assertNull(agentContextLeftLabel(AgentContext(used = 0, window = 0)))
    }

    @Test
    fun allAgentsShowsOnlyUnderTwenty() {
        assertNull(allAgentsContextSuffix(AgentContext(used = 100_000, window = 200_000)))
        assertEquals("15% context left", allAgentsContextSuffix(AgentContext(used = 170_000, window = 200_000)))
    }
}
