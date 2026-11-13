package sh.simple.android.ui.pane

import org.junit.Assert.assertEquals
import org.junit.Test
import sh.simple.android.model.PaneTab

class PaneTabLabelsTest {
    @Test
    fun chipLabelUsesAgentTitleAfterChevron() {
        assertEquals(
            "Brain worker continuation",
            cleanHerdrTabTitle("1 · claude › Brain worker continuation"),
        )
    }

    @Test
    fun chipLabelDropsLonelyIndexPrefix() {
        assertEquals("simple-app", cleanHerdrTabTitle("3 · simple-app"))
    }

    @Test
    fun duplicateTitlesKeepNumericPrefix() {
        val tabs = listOf(
            PaneTab(id = "t1", label = "1 · claude › Alpha"),
            PaneTab(id = "t2", label = "2 · claude › Alpha"),
        )
        assertEquals(listOf("1 · Alpha", "2 · Alpha"), herdrTabChipLabels(tabs))
    }
}
