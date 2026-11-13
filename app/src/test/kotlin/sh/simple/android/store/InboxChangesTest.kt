package sh.simple.android.store

import org.junit.Assert.assertEquals
import org.junit.Test
import sh.simple.android.model.Item

class InboxChangesTest {
    private fun item(id: String, resolved: Boolean = false) =
        Item(id = id, agent = "t1", kind = "prompt", title = id, created_ms = 1, resolved = resolved)

    @Test
    fun itemsThatArriveWhileDisconnectedAreAnnounced() {
        val changes = inboxChanges(before = listOf(item("a")), after = listOf(item("a"), item("b")))
        assertEquals(listOf("b"), changes.added.map { it.id })
        assertEquals(emptyList<String>(), changes.gone.map { it.id })
    }

    @Test
    fun itemsResolvedOrRemovedWhileDisconnectedAreCleared() {
        val changes = inboxChanges(
            before = listOf(item("a"), item("b")),
            after = listOf(item("a", resolved = true)),
        )
        assertEquals(emptyList<String>(), changes.added.map { it.id })
        assertEquals(setOf("a", "b"), changes.gone.map { it.id }.toSet())
        assertEquals(true, changes.gone.all { it.resolved })
    }
}
