package sh.simple.android.ui.composer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sh.simple.android.store.AgentEntity

class WorkspacePathsTest {
    @Test
    fun prefixMatchStopsAtPathSeparator() {
        val root = "/home/user/projects/simple"
        assertTrue(pathHasPrefix("/home/user/projects/simple", root))
        assertTrue(pathHasPrefix("/home/user/projects/simple/src", root))
        assertFalse(pathHasPrefix("/home/user/projects/simple-extra", root))
        assertFalse(pathHasPrefix("/home/user/projects/simpleton", root))
    }

    @Test
    fun agentBelongsUsesSeparatorSafePrefix() {
        val agent = AgentEntity(
            hostId = "h1",
            id = "a1",
            kind = "claude",
            title = "t",
            cwd = "/home/user/projects/simple-extra",
            workspace = "/home/user/projects/simple-extra",
            status = "working",
            waiting = null,
            model = null,
            mode = null,
            updated = 0L,
            pinned = false,
            unread = false,
            json = "{}",
        )
        assertFalse(agentBelongsToWorkspace(agent, "/home/user/projects/simple"))
        assertTrue(agentBelongsToWorkspace(agent, "/home/user/projects/simple-extra"))
    }
}
