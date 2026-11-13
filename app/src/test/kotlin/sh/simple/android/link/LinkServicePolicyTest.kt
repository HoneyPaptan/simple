package sh.simple.android.link

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkServicePolicyTest {
    @Test
    fun noServiceWhileAppIsForeground() {
        assertFalse(shouldRunLinkForegroundService(true, 2, 3, appInForeground = true))
        assertFalse(shouldRunLinkForegroundService(false, 2, 3, appInForeground = true))
    }

    @Test
    fun backgroundAlertsWithPairedHosts() {
        assertTrue(shouldRunLinkForegroundService(true, 1, 0, appInForeground = false))
        assertTrue(shouldRunLinkForegroundService(true, 2, 0, appInForeground = false))
        assertFalse(shouldRunLinkForegroundService(true, 0, 0, appInForeground = false))
        assertTrue(shouldRunLinkForegroundService(true, 0, 2, appInForeground = false))
    }

    @Test
    fun openItemsWithoutBackgroundAlerts() {
        assertTrue(shouldRunLinkForegroundService(false, 0, 1, appInForeground = false))
        assertTrue(shouldRunLinkForegroundService(false, 2, 1, appInForeground = false))
        assertFalse(shouldRunLinkForegroundService(false, 0, 0, appInForeground = false))
        assertFalse(shouldRunLinkForegroundService(false, 2, 0, appInForeground = false))
    }
}
