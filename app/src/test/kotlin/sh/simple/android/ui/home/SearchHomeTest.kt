package sh.simple.android.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test
import sh.simple.android.store.AgentEntity
import sh.simple.android.store.WorkspaceEntity

class SearchHomeTest {
    private fun agent(id: String, title: String, kind: String, ws: String) =
        AgentEntity("h", id, kind, title, ws, ws, "idle", null, null, null, 0, false, false, "{}")

    private fun workspace(name: String, path: String) =
        WorkspaceEntity("h", path, name, null, false, 0, false, 0, "{}")

    @Test
    fun matchesTitleKindAndWorkspace() {
        val agents = listOf(
            agent("t1", "Repo purpose overview", "claude", "/p/simple"),
            agent("t2", "Shell", "shell", "/tmp"),
            agent("t3", "OC | Greeting", "opencode", "/p/api"),
        )
        val spaces = listOf(workspace("simple", "/p/simple"), workspace("tmp", "/tmp"))
        assertEquals(listOf("t1"), searchHome("purpose", agents, spaces).agents.map { it.id })
        assertEquals(listOf("t3"), searchHome("opencode", agents, spaces).agents.map { it.id })
        val bySpace = searchHome("SIMPLE", agents, spaces)
        assertEquals(listOf("t1"), bySpace.agents.map { it.id })
        assertEquals(listOf("simple"), bySpace.workspaces.map { it.name })
        assertEquals(SearchResults(emptyList(), emptyList()), searchHome("  ", agents, spaces))
    }
}
