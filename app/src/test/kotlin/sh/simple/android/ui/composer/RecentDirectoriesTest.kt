package sh.simple.android.ui.composer

import org.junit.Assert.assertEquals
import org.junit.Test
import sh.simple.android.store.AgentEntity
import sh.simple.android.store.WorkspaceEntity

class RecentDirectoriesTest {
    @Test
    fun tildeDisplayShortensHome() {
        assertEquals("~/proj", pathWithTilde("/home/me/proj"))
        assertEquals("~", pathWithTilde("/home/me"))
    }

    @Test
    fun recentMergesWorkspaceAndAgentNewestFirst() {
        val workspaces = listOf(
            WorkspaceEntity("h1", "/a", "a", null, false, 0, false, 100L, "{}"),
            WorkspaceEntity("h1", "/b", "b", null, false, 0, false, 50L, "{}"),
        )
        val agents = listOf(
            agent("h1", "/c", 200L),
            agent("h1", "/a", 150L),
        )
        assertEquals(listOf("/c", "/a", "/b"), recentDirectoriesForHost("h1", workspaces, agents))
    }

    @Test
    fun recentRespectsHostAndLimit() {
        val workspaces = (1..10).map { i ->
            WorkspaceEntity("h1", "/w$i", "w$i", null, false, 0, false, i.toLong(), "{}")
        }
        assertEquals(8, recentDirectoriesForHost("h1", workspaces, emptyList(), limit = 8).size)
        assertEquals(emptyList<String>(), recentDirectoriesForHost("h2", workspaces, emptyList()))
    }

    private fun agent(hostId: String, cwd: String, updated: Long) = AgentEntity(
        hostId = hostId,
        id = "$cwd-$updated",
        kind = "claude",
        title = "t",
        cwd = cwd,
        workspace = "",
        status = "idle",
        waiting = null,
        model = null,
        mode = null,
        updated = updated,
        pinned = false,
        unread = false,
        json = "{}",
    )
}
