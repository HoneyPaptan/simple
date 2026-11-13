package sh.simple.android.ui.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScreenBackStackTest {
    @Test
    fun pairingWithPendingUriIsNotSaved() {
        assertNull(encodeScreen(Screen.Pairing(manualEntry = true, pendingUri = "simple://pair?t=1")))
    }

    @Test
    fun roundTripEveryScreenSubclass() {
        val screens: List<Screen> = listOf(
            Screen.Welcome,
            Screen.Pairing(manualEntry = false),
            Screen.Pairing(manualEntry = true),
            Screen.Today,
            Screen.Workspaces,
            Screen.AllAgents,
            Screen.NeedsYou,
            Screen.Workspace("h1", "/home/dev/project"),
            Screen.Chat("h1", "a1"),
            Screen.AgentScreen("h1", "a2"),
            Screen.Files("h1", "~/src"),
            Screen.FileView("h1", "/etc/hosts"),
            Screen.Changes("h1", "/repo"),
            Screen.Diff("h1", "/repo", "main.kt"),
            Screen.Usage("h1"),
            Screen.Servers("h1"),
            Screen.Preview("h1", 3847, "/v1/preview/x/", "sess"),
            Screen.Webhooks("h1"),
            Screen.Settings,
            Screen.Voice,
            Screen.CodeFont,
            Screen.KitGallery,
        )
        for (screen in screens) {
            val enc = encodeScreen(screen)
            requireNotNull(enc) { screen }
            assertEquals(screen, decodeScreen(enc))
        }
        val stack = screens
        assertEquals(stack, decodeBackStack(encodeBackStack(stack)))
    }
}
