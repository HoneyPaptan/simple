package sh.simple.android.ui.nav

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsReturnTest {
    @Test
    fun usageReopensSettingsOnlyFromSettings() {
        val usage = Screen.Usage("host")
        assertTrue(shouldReopenSettingsAfterPop(usage, usageFromSettings = true))
        assertFalse(shouldReopenSettingsAfterPop(usage, usageFromSettings = false))
    }

    @Test
    fun otherSettingsScreensStillReopen() {
        assertTrue(shouldReopenSettingsAfterPop(Screen.Voice, usageFromSettings = false))
        assertFalse(shouldReopenSettingsAfterPop(Screen.Chat("h", "a"), usageFromSettings = false))
    }
}
