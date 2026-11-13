package sh.simple.android.ui.inbox

import org.junit.Assert.assertEquals
import org.junit.Test
import sh.simple.android.model.Item

class ApprovalTextTest {
    @Test
    fun subtitleOmitsDuplicateToolName() {
        val item = Item(
            id = "i1",
            agent = "t1",
            kind = "approval",
            title = "Bash",
            tool = "Bash",
            command = "go test ./...",
            created_ms = 1,
        )
        assertEquals("", approvalCardSubtitle(item))
        assertEquals("go test ./...", approvalCardCommand(item))
    }

    @Test
    fun subtitleShowsToolWhenTitleIsDescription() {
        val item = Item(
            id = "i2",
            agent = "t1",
            kind = "approval",
            title = "Clean build",
            tool = "Bash",
            command = "npm run build",
            created_ms = 1,
        )
        assertEquals("Bash", approvalCardSubtitle(item))
    }
}
