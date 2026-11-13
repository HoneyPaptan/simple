package sh.simple.android.ui.composer

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkspaceTargetTest {
    @Test
    fun targetLabelPutsFolderNameFirst() {
        val rows = targetsFromWorkspaces(
            mapOf("h1" to "instabox-simple-long-name"),
            listOf(
                sh.simple.android.store.WorkspaceEntity(
                    hostId = "h1",
                    path = "/home/demo",
                    name = "demo",
                    branch = null,
                    dirty = false,
                    agents = 1,
                    pinned = false,
                    updated = 0L,
                    json = "{}",
                ),
            ),
            "h1",
            showComputerNames = true,
        )
        assertEquals("demo · instabox-simple-long-name", rows.first().label)
    }

    @Test
    fun targetLabelOmitsComputerWhenOnlyOnePaired() {
        val rows = targetsFromWorkspaces(
            mapOf("h1" to "simple"),
            listOf(
                sh.simple.android.store.WorkspaceEntity(
                    hostId = "h1",
                    path = "/home/demo",
                    name = "demo",
                    branch = null,
                    dirty = false,
                    agents = 1,
                    pinned = false,
                    updated = 0L,
                    json = "{}",
                ),
            ),
            "h1",
            showComputerNames = false,
        )
        assertEquals("demo", rows.first().label)
    }
}
