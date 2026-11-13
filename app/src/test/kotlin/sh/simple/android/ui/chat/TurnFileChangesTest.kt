package sh.simple.android.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test
import sh.simple.android.model.Tool

class TurnFileChangesTest {
    @Test
    fun mergesRepeatedEditsAndCountsLines() {
        val tools = listOf(
            toolEntry("a", Tool("Edit", "main.go", status = "ok", diff = "---\n+++ \n-old\n+new\n+line")),
            toolEntry("b", Tool("Edit", "main.go", status = "ok", diff = "+extra\n-remove")),
            toolEntry("c", Tool("Read", "other", status = "ok")),
        )
        val changes = turnFileChanges(tools)
        assertEquals(1, changes.size)
        assertEquals("main.go", changes[0].path)
        assertEquals(3, changes[0].adds)
        assertEquals(2, changes[0].removes)
    }

    @Test
    fun workedPillUsesStepsOnlyWithoutDuration() {
        assertEquals("Worked 3 steps", workedPillLabel(3, null))
        assertEquals("Worked 1 step", workedPillLabel(1, null))
        assertEquals("Worked 2m · 4 steps", workedPillLabel(4, 120_000L))
    }

    @Test
    fun editWithoutDiffStillCountsAsChangedFile() {
        val changes = turnFileChanges(listOf(toolEntry("a", Tool("Edit", "opencode.txt", status = "ok"))))
        assertEquals(listOf(TurnFileChange("opencode.txt", 0, 0)), changes)
    }
}
