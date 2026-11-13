package sh.simple.android.ui.composer

import org.junit.Assert.assertEquals
import org.junit.Test
import sh.simple.android.model.FileEntry
import sh.simple.android.store.AgentEntity
import sh.simple.android.store.WorkspaceEntity

class FolderBrowserTest {
    @Test
    fun dirListingPutsDotFoldersLast() {
        val entries = listOf(
            FileEntry(".hidden", true, 0, 0),
            FileEntry("visible", true, 0, 0),
            FileEntry(".adal", true, 0, 0),
            FileEntry("b", true, 0, 0),
        )
        val split = folderDirListing(entries)
        assertEquals(listOf("b", "visible"), split.visible.map { it.name })
        assertEquals(listOf(".adal", ".hidden"), split.hidden.map { it.name })
    }

    @Test
    fun homeWorkspacesHideMissingWhenNoAgent() {
        val agents = emptyList<AgentEntity>()
        val workspaces = listOf(
            WorkspaceEntity("h", "/gone", "gone", null, false, 0, false, 0, "{}"),
            WorkspaceEntity("h", "/live", "live", null, false, 0, false, 0, "{}"),
        )
        val visible = homeWorkspacesVisible(workspaces, agents, setOf("/live"))
        assertEquals(listOf("/live"), visible.map { it.path })
    }

    @Test
    fun homeWorkspacesKeepMissingWhenAgentPresent() {
        val workspaces = listOf(
            WorkspaceEntity("h", "/gone", "gone", null, false, 0, false, 0, "{}"),
        )
        val agents = listOf(
            AgentEntity(
                "h",
                "a1",
                "claude",
                "t",
                "/gone",
                "/gone",
                "working",
                null,
                null,
                null,
                0,
                false,
                false,
                "{}",
            ),
        )
        val visible = homeWorkspacesVisible(workspaces, agents, emptySet())
        assertEquals(1, visible.size)
    }

    @Test
    fun filterRecentDropsMissing() {
        assertEquals(listOf("/a"), filterRecentPaths(listOf("/a", "/b"), setOf("/a")))
    }
}
