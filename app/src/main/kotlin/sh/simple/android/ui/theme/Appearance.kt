package sh.simple.android.ui.theme

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

enum class ColorSchemePreference {
    System,
    Light,
    Dark,
    ;

    companion object {
        fun fromStored(value: String?): ColorSchemePreference = when (value) {
            "light" -> Light
            "dark" -> Dark
            else -> System
        }
    }

    fun stored(): String = when (this) {
        System -> "system"
        Light -> "light"
        Dark -> "dark"
    }
}

data class AppearanceSettings(
    val blur: Int = 28,
    val tint: Int = 75,
    val lensStrength: Int = 100,
    val depthEffect: Boolean = false,
    val chromaticAberration: Boolean = false,
    val brightness: Float = 0.08f,
    val contrast: Float = 1f,
    val saturation: Float = 1.7f,
    val lightEdge: Boolean = true,
    val glassShadow: Boolean = true,
    val liquidGlass: Boolean = true,
    val colorScheme: ColorSchemePreference = ColorSchemePreference.System,
    val themeId: ThemeId = ThemeId.Simple,
) {
    companion object {
        val defaults = AppearanceSettings()
    }
    val fillScale: Float get() = tint / 75f

    fun resolvesDark(systemDark: Boolean): Boolean {
        if (themeId.darkOnly) return true
        return when (colorScheme) {
            ColorSchemePreference.System -> systemDark
            ColorSchemePreference.Light -> false
            ColorSchemePreference.Dark -> true
        }
    }

    fun resolveColors(systemDark: Boolean): SimpleColors =
        AppPalettes.palette(themeId, resolvesDark(systemDark))

    fun usesLightSystemBars(systemDark: Boolean): Boolean = !resolvesDark(systemDark)
}

val LocalAppearance = staticCompositionLocalOf { AppearanceSettings() }

private val Context.appearanceStore: DataStore<Preferences> by preferencesDataStore("appearance")

private object Keys {
    val blur = intPreferencesKey("blur")
    val tint = intPreferencesKey("tint")
    val lens = intPreferencesKey("lens")
    val brightness = floatPreferencesKey("brightness")
    val contrast = floatPreferencesKey("contrast")
    val saturation = floatPreferencesKey("saturation")
    val lightEdge = booleanPreferencesKey("light_edge")
    val glassShadow = booleanPreferencesKey("glass_shadow")
    val depthEffect = booleanPreferencesKey("depth_effect")
    val chromaticAberration = booleanPreferencesKey("chromatic_aberration")
    val liquidGlass = booleanPreferencesKey("liquid_glass")
    val reduceTransparency = booleanPreferencesKey("reduce_transparency")
    val colorScheme = stringPreferencesKey("color_scheme")
    val themeId = stringPreferencesKey("theme_id")
}

class AppearanceController(context: Context) {
    private val store = context.applicationContext.appearanceStore
    var settings by mutableStateOf(AppearanceSettings())
        private set
    var loaded by mutableStateOf(false)
        private set

    init {
        CoroutineScope(Dispatchers.Main.immediate).launch {
            store.data.map { prefs ->
                val migratedLiquid = when {
                    prefs[Keys.liquidGlass] != null -> prefs[Keys.liquidGlass]!!
                    prefs[Keys.reduceTransparency] != null -> !prefs[Keys.reduceTransparency]!!
                    else -> true
                }
                AppearanceSettings(
                    blur = prefs[Keys.blur] ?: 28,
                    tint = prefs[Keys.tint] ?: 75,
                    lensStrength = prefs[Keys.lens] ?: 100,
                    depthEffect = prefs[Keys.depthEffect] ?: false,
                    chromaticAberration = prefs[Keys.chromaticAberration] ?: false,
                    brightness = prefs[Keys.brightness] ?: 0.08f,
                    contrast = prefs[Keys.contrast] ?: 1f,
                    saturation = prefs[Keys.saturation] ?: 1.7f,
                    lightEdge = prefs[Keys.lightEdge] ?: true,
                    glassShadow = prefs[Keys.glassShadow] ?: true,
                    liquidGlass = migratedLiquid,
                    colorScheme = ColorSchemePreference.fromStored(prefs[Keys.colorScheme]),
                    themeId = ThemeId.fromStored(prefs[Keys.themeId]),
                )
            }.flowOn(Dispatchers.IO).collect {
                settings = it
                loaded = true
            }
        }
    }

    fun update(transform: (AppearanceSettings) -> AppearanceSettings) {
        val next = transform(settings)
        settings = next
        CoroutineScope(Dispatchers.IO).launch {
            store.edit { prefs ->
                prefs[Keys.blur] = next.blur
                prefs[Keys.tint] = next.tint
                prefs[Keys.lens] = next.lensStrength
                prefs[Keys.depthEffect] = next.depthEffect
                prefs[Keys.chromaticAberration] = next.chromaticAberration
                prefs[Keys.brightness] = next.brightness
                prefs[Keys.contrast] = next.contrast
                prefs[Keys.saturation] = next.saturation
                prefs[Keys.lightEdge] = next.lightEdge
                prefs[Keys.glassShadow] = next.glassShadow
                prefs[Keys.liquidGlass] = next.liquidGlass
                prefs.remove(Keys.reduceTransparency)
                prefs[Keys.colorScheme] = next.colorScheme.stored()
                prefs[Keys.themeId] = next.themeId.stored
            }
        }
    }
}

@Composable
fun rememberAppearanceController(): AppearanceController {
    val context = LocalContext.current
    return remember(context) { AppearanceController(context) }
}
