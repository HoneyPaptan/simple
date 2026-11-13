package sh.simple.android.store

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import sh.simple.android.ui.theme.AppearanceSettings

@Serializable
data class SettingsExportComputer(
    val name: String,
    val addresses: String,
    val fingerprint: String,
)

@Serializable
data class SettingsExportDocument(
    val version: Int,
    val background_alerts: Boolean = true,
    val appearance: SettingsExportAppearance = SettingsExportAppearance(),
    val computers: List<SettingsExportComputer> = emptyList(),
)

@Serializable
data class SettingsExportAppearance(
    val theme_id: String = "simple",
    val color_scheme: String = "system",
    val liquid_glass: Boolean = true,
    val blur: Int = 28,
    val tint: Int = 75,
    val lens_strength: Int = 100,
    val depth_effect: Boolean = false,
    val chromatic_aberration: Boolean = false,
    val brightness: Float = 0.08f,
    val contrast: Float = 1f,
    val saturation: Float = 1.7f,
    val light_edge: Boolean = true,
    val glass_shadow: Boolean = true,
)

object SettingsTransfer {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(
        app: AppSettings,
        appearance: AppearanceSettings,
        computers: List<HostEntity>,
    ): String {
        val doc = SettingsExportDocument(
            version = 1,
            background_alerts = app.backgroundAlerts,
            appearance = SettingsExportAppearance(
                theme_id = appearance.themeId.stored,
                color_scheme = appearance.colorScheme.stored(),
                liquid_glass = appearance.liquidGlass,
                blur = appearance.blur,
                tint = appearance.tint,
                lens_strength = appearance.lensStrength,
                depth_effect = appearance.depthEffect,
                chromatic_aberration = appearance.chromaticAberration,
                brightness = appearance.brightness,
                contrast = appearance.contrast,
                saturation = appearance.saturation,
                light_edge = appearance.lightEdge,
                glass_shadow = appearance.glassShadow,
            ),
            computers = computers.map {
                SettingsExportComputer(
                    name = it.name,
                    addresses = it.addresses,
                    fingerprint = it.fingerprint,
                )
            },
        )
        return json.encodeToString(doc)
    }

    fun decode(raw: String): SettingsExportDocument {
        val doc = json.decodeFromString<SettingsExportDocument>(raw)
        require(doc.version == 1) { "bad_version" }
        return doc
    }

    fun appearanceFromExport(src: SettingsExportAppearance): AppearanceSettings {
        return AppearanceSettings(
            themeId = sh.simple.android.ui.theme.ThemeId.fromStored(src.theme_id),
            colorScheme = sh.simple.android.ui.theme.ColorSchemePreference.fromStored(src.color_scheme),
            liquidGlass = src.liquid_glass,
            blur = src.blur,
            tint = src.tint,
            lensStrength = src.lens_strength,
            depthEffect = src.depth_effect,
            chromaticAberration = src.chromatic_aberration,
            brightness = src.brightness,
            contrast = src.contrast,
            saturation = src.saturation,
            lightEdge = src.light_edge,
            glassShadow = src.glass_shadow,
        )
    }

    fun previewSummary(doc: SettingsExportDocument): String {
        val alerts = if (doc.background_alerts) "Alerts on" else "Alerts off"
        val scheme = when (doc.appearance.color_scheme) {
            "dark" -> "Dark"
            "light" -> "Light"
            else -> "System"
        }
        val glass = if (doc.appearance.liquid_glass) "Liquid glass on" else "Liquid glass off"
        val names = doc.computers.joinToString { it.name }
        val computers = if (doc.computers.isEmpty()) {
            "No computers"
        } else {
            val noun = if (doc.computers.size == 1) "computer" else "computers"
            "${doc.computers.size} $noun: $names"
        }
        return "$alerts, $scheme, $glass, $computers"
    }
}
