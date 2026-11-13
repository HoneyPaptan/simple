package sh.simple.android.model

import org.junit.Assert.assertEquals
import org.junit.Test

class NullListsTest {
    @Test
    fun helloSnapshotWithNullListsDecodes() {
        val raw = """{"host":{"id":"h_1","name":"box","version":"0.1.0"},"seq":6,"replay":false,""" +
            """"snapshot":{"agents":[{"id":"t1","kind":"shell","title":"bash","cwd":"/tmp","workspace":"/tmp",""" +
            """"status":"unknown","started_ms":1,"updated_ms":2}],"inbox":null,"workspaces":null}}"""
        val hello = WireJson.decodeFromString<HelloResult>(raw)
        assertEquals(1, hello.snapshot!!.agents.size)
        assertEquals(0, hello.snapshot!!.inbox.size)
        assertEquals(0, hello.snapshot!!.workspaces.size)
    }
}
