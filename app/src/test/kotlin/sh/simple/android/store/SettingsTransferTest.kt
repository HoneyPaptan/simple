package sh.simple.android.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import sh.simple.android.ui.theme.AppearanceSettings

class SettingsTransferTest {
    @Test
    fun roundTripExport() {
        val raw = SettingsTransfer.encode(
            AppSettings(backgroundAlerts = false),
            AppearanceSettings(),
            listOf(
                HostEntity("h1", "box", "127.0.0.1:47420", "fp", "cipher", "e1", 0),
            ),
        )
        assertTrue(raw.contains("\"version\":1"))
        assertTrue(raw.contains("\"color_scheme\":\"system\""))
        assertFalse(raw.contains("cipher"))
        val doc = SettingsTransfer.decode(raw)
        assertEquals(false, doc.background_alerts)
        assertEquals(1, doc.computers.size)
        assertEquals("box", doc.computers[0].name)
    }

    @Test
    fun rejectBadVersion() {
        assertThrows(IllegalArgumentException::class.java) {
            SettingsTransfer.decode("""{"version":99}""")
        }
    }

    @Test
    fun rejectMissingVersion() {
        assertThrows(IllegalArgumentException::class.java) {
            SettingsTransfer.decode("""{"computers":[]}""")
        }
    }

    @Test
    fun previewSummaryUsesWords() {
        val doc = SettingsExportDocument(
            version = 1,
            background_alerts = true,
            appearance = SettingsExportAppearance(color_scheme = "dark", liquid_glass = false),
            computers = listOf(
                SettingsExportComputer("box", "127.0.0.1:47420", "fp1"),
                SettingsExportComputer("laptop", "127.0.0.1:47421", "fp2"),
            ),
        )
        assertEquals(
            "Alerts on, Dark, Liquid glass off, 2 computers: box, laptop",
            SettingsTransfer.previewSummary(doc),
        )
    }

    @Test
    fun previewSummarySingularComputer() {
        val doc = SettingsExportDocument(
            version = 1,
            background_alerts = false,
            appearance = SettingsExportAppearance(color_scheme = "light"),
            computers = listOf(SettingsExportComputer("box", "127.0.0.1:47420", "fp1")),
        )
        assertEquals(
            "Alerts off, Light, Liquid glass on, 1 computer: box",
            SettingsTransfer.previewSummary(doc),
        )
    }

    @Test
    fun appearanceRoundTripInExport() {
        val appearance = AppearanceSettings(
            lensStrength = 42,
            depthEffect = true,
            chromaticAberration = true,
            brightness = 0.2f,
            contrast = 1.2f,
            saturation = 1.1f,
            lightEdge = false,
            glassShadow = false,
        )
        val raw = SettingsTransfer.encode(AppSettings(), appearance, emptyList())
        assertTrue(raw.contains("\"lens_strength\":42"))
        assertTrue(raw.contains("\"depth_effect\":true"))
        assertTrue(raw.contains("\"glass_shadow\":false"))
        val restored = SettingsTransfer.appearanceFromExport(SettingsTransfer.decode(raw).appearance)
        assertEquals(appearance, restored)
    }
}
