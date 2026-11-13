package sh.simple.android.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import sh.simple.android.store.AgentEntity
import sh.simple.android.ui.kit.BrandIcons
import sh.simple.android.ui.kit.Icons

class AgentUiTest {
    @Test
    fun labelsForTerminalKinds() {
        assertEquals("Gemini", agentKindLabel("gemini"))
        assertEquals("Qwen", agentKindLabel("qwen"))
        assertEquals("Copilot", agentKindLabel("copilot"))
        assertEquals("Amp", agentKindLabel("amp"))
        assertEquals("Goose", agentKindLabel("goose"))
        assertEquals("Crush", agentKindLabel("crush"))
        assertEquals("Aider", agentKindLabel("aider"))
        assertEquals("Kimi", agentKindLabel("kimi"))
    }

    @Test
    fun newAgentsPickerOrdersInstalled() {
        val installed = mapOf(
            "shell" to true,
            "opencode" to true,
            "claude" to true,
            "cursor" to false,
        )
        assertEquals(listOf("claude", "opencode", "shell"), installedNewAgentKinds(installed))
    }

    @Test
    fun allAgentsRecentsSkipsWaitingAndSortsByUpdated() {
        val agents = listOf(
            AgentEntity("h", "a", "claude", "A", "/w", "/w", "working", null, null, null, 100, false, false, "{}"),
            AgentEntity("h", "b", "claude", "B", "/w", "/w", "waiting", null, null, null, 300, false, false, "{}"),
            AgentEntity("h", "c", "claude", "C", "/w", "/w", "done", null, null, null, 200, false, false, "{}"),
        )
        assertEquals(listOf("c", "a"), allAgentsRecents(agents).map { it.id })
        assertEquals(listOf("b", "c", "a"), allAgentsOrder(agents).map { it.id })
    }

    @Test
    fun terminalKindIconsUseGenericTerminal() {
        assertSame(Icons.Term, agentKindIcon("gemini"))
        assertSame(Icons.Term, agentKindIcon("goose"))
        assertSame(BrandIcons.Codex, agentKindIcon("codex"))
    }

  private fun stubAgent(id: String, runtime: String, status: String = "done") = AgentEntity(
        "h",
        id,
        "claude",
        id,
        "/w",
        "/w",
        status,
        null,
        null,
        null,
        0L,
        false,
        false,
        """{"id":"$id","runtime":"$runtime","kind":"claude","title":"$id","cwd":"/w","workspace":"/w","status":"$status","started_ms":0,"updated_ms":0}""",
    )

    @Test
    fun runtimeSectionsOmitTitlesForOneRuntime() {
        val only = listOf(stubAgent("a", "hc"))
        assertEquals(listOf(null to only), agentsByRuntimeSections(only))
    }

    @Test
    fun runtimeSectionsSplitHcAndHerdr() {
        val hc = stubAgent("a", "hc")
        val herdr = stubAgent("b", "herdr")
        val sections = agentsByRuntimeSections(listOf(hc, herdr))
        assertEquals(2, sections.size)
        assertEquals("herdr-core", sections[0].first)
        assertEquals(listOf("a"), sections[0].second.map { it.id })
        assertEquals("herdr", sections[1].first)
        assertEquals(listOf("b"), sections[1].second.map { it.id })
    }

    @Test
    fun runtimeLabelMapsHCToHerdrCore() {
        assertEquals("herdr-core", agentRuntimeLabel("hc"))
        assertEquals("herdr", agentRuntimeLabel("herdr"))
    }

    @Test
    fun todayTilePagesChunkFourPerPageKeepingOrder() {
        val agents = (1..9).map { n ->
            AgentEntity(
                "h",
                "a$n",
                "claude",
                "A$n",
                "/w",
                "/w",
                "done",
                null,
                null,
                null,
                n.toLong(),
                false,
                false,
                "{}",
            )
        }.reversed()
        val pages = todayTilePages(agents)
        assertEquals(3, pages.size)
        assertEquals(4, pages[0].size)
        assertEquals(4, pages[1].size)
        assertEquals(1, pages[2].size)
        assertEquals(
            listOf("a9", "a8", "a7", "a6", "a5", "a4", "a3", "a2", "a1"),
            pages.flatten().map { it.id },
        )
    }

    @Test
    fun todayHomeTilesPreferWorkingThenNeedsYouThenRecencyAndCap() {
        val agents = listOf(
            AgentEntity("h", "idle", "claude", "I", "/w", "/w", "idle", null, null, null, 50, false, false, "{}"),
            AgentEntity("h", "work", "claude", "W", "/w", "/w", "working", null, null, null, 10, false, false, "{}"),
            AgentEntity("h", "wait", "claude", "N", "/w", "/w", "waiting", null, null, null, 100, false, false, "{}"),
            AgentEntity("h", "recent", "claude", "R", "/w", "/w", "done", null, null, null, 400, false, false, "{}"),
            AgentEntity("h", "old", "claude", "O", "/w", "/w", "done", null, null, null, 300, false, false, "{}"),
            AgentEntity("h", "extra", "claude", "X", "/w", "/w", "done", null, null, null, 500, false, false, "{}"),
        )
        assertEquals(
            listOf("work", "wait", "extra", "recent"),
            todayHomeTileAgents(agents).map { it.id },
        )
    }

    @Test
    fun unknownInstalledKindsShowEveryAgent() {
        assertEquals(newAgentKindOrder, installedNewAgentKinds(emptyMap()))
    }
}
