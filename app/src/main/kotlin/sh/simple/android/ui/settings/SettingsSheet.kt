package sh.simple.android.ui.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import android.os.Build
import sh.simple.android.link.Hosts
import sh.simple.android.store.AppSettingsStore
import sh.simple.android.store.CodeFontStore
import sh.simple.android.store.TerminalKeyBarStore
import sh.simple.android.store.TerminalThemeStore
import sh.simple.android.ui.terminal.TerminalThemes
import sh.simple.android.store.SimpleDatabase
import sh.simple.android.ui.kit.GlassSheet
import sh.simple.android.ui.kit.GlassSlider
import sh.simple.android.ui.kit.GlassFloatSlider
import sh.simple.android.ui.kit.SettingInfoTag
import sh.simple.android.ui.kit.ListRow
import sh.simple.android.ui.kit.SectionLabel
import sh.simple.android.ui.kit.Segmented
import sh.simple.android.ui.kit.Toggle
import sh.simple.android.ui.kit.AppText
import sh.simple.android.ui.theme.AppearanceController
import sh.simple.android.ui.theme.AppearanceSettings
import sh.simple.android.ui.theme.Type
import sh.simple.android.ui.theme.Tokens
import sh.simple.android.ui.glass.isDebuggableApp
import androidx.compose.ui.platform.LocalContext
import sh.simple.android.ui.security.deviceHasScreenLock

private val SettingsSectionPadding = Modifier.padding(horizontal = 20.dp)
private val SettingsRowPadding = Modifier.padding(start = 51.dp, end = 20.dp)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsSheet(
    open: Boolean,
    onDismiss: () -> Unit,
    db: SimpleDatabase,
    hosts: Hosts,
    appSettings: AppSettingsStore,
    linkLifecycle: sh.simple.android.link.LinkLifecycle,
    appearance: AppearanceController,
    codeFont: CodeFontStore,
    terminalTheme: TerminalThemeStore,
    terminalKeys: TerminalKeyBarStore,
    onOpenKitGallery: () -> Unit = {},
    onOpenComputers: () -> Unit = {},
    onOpenCodeFont: () -> Unit = {},
    onOpenTerminalTheme: () -> Unit = {},
    onOpenTerminalKeys: () -> Unit = {},
    onOpenUsage: (String) -> Unit = {},
    onOpenWebhooks: (String) -> Unit = {},
    onOpenVoice: () -> Unit = {},
    scrollState: ScrollState = rememberScrollState(),
) {
    val hostRows by db.hosts().observeAll().collectAsState(initial = emptyList())
    val settings = appearance.settings
    val appPrefs = appSettings.settings
    var transferStatus by remember { mutableStateOf("") }
    GlassSheet(open, onDismiss, "Settings", doneLabel = "Done", onDone = onDismiss) {
        Column(Modifier.verticalScroll(scrollState).padding(horizontal = 16.dp)) {
            if (transferStatus.isNotEmpty()) {
                AppText(transferStatus, Modifier.padding(20.dp), style = Type.meta, color = Tokens.current.muted)
            }
            SectionLabel("Computers")
            val nameRows = sh.simple.android.ui.util.showPairedComputerNames(hostRows.size)
            for (host in hostRows) {
                ListRow(
                    sh.simple.android.ui.kit.Icons.Server,
                    host.name,
                    onClick = onOpenComputers,
                )
                ListRow(
                    sh.simple.android.ui.kit.Icons.Bolt,
                    "Usage",
                    secondary = if (nameRows) host.name else "",
                    onClick = { onOpenUsage(host.id) },
                )
                ListRow(
                    sh.simple.android.ui.kit.Icons.Bell,
                    "Webhooks",
                    secondary = if (nameRows) host.name else "",
                    onClick = { onOpenWebhooks(host.id) },
                )
            }
            SectionLabel("Privacy")
            val context = LocalContext.current
            val deviceSecure = remember(context) { deviceHasScreenLock(context) }
            Toggle(
                checked = appPrefs.appLock && deviceSecure,
                onChange = { on ->
                    if (deviceSecure) appSettings.setAppLock(on)
                },
                modifier = SettingsRowPadding,
            ) {
                AppText(
                    "Lock with fingerprint or screen lock",
                    style = Type.body,
                )
            }
            if (!deviceSecure) {
                AppText(
                    "Set a screen lock on this device before turning this on.",
                    SettingsRowPadding.padding(bottom = 8.dp),
                    style = Type.meta,
                    color = Tokens.current.muted,
                )
            }
            SectionLabel("Alerts")
            Toggle(
                checked = appPrefs.backgroundAlerts,
                onChange = { on ->
                    appSettings.setBackgroundAlerts(on)
                    linkLifecycle.onAppSettingsChanged()
                },
                modifier = SettingsRowPadding,
            ) {
                AppText(
                    "Stay connected so agents can reach you. Uses a small persistent notification.",
                    style = Type.body,
                )
            }
            SectionLabel("Appearance")
            val codeFontLabel = if (codeFont.choice.mode == "host" && codeFont.choice.label.isNotEmpty()) {
                codeFont.choice.label
            } else {
                "Geist Mono"
            }
            ListRow(
                sh.simple.android.ui.kit.Icons.Text,
                "Code font",
                secondary = codeFontLabel,
                onClick = onOpenCodeFont,
            )
            ListRow(
                sh.simple.android.ui.kit.Icons.Term,
                "Terminal theme",
                secondary = TerminalThemes.labelFor(terminalTheme.themeId),
                onClick = onOpenTerminalTheme,
            )
            val keyCount = terminalKeys.entries.size
            ListRow(
                sh.simple.android.ui.kit.Icons.Grid,
                "Terminal keys",
                secondary = if (keyCount == 0) "None" else "$keyCount custom",
                onClick = onOpenTerminalKeys,
            )
            AppearanceSliders(settings, appearance)
            SectionLabel("Voice")
            ListRow(
                sh.simple.android.ui.kit.Icons.Mic,
                "Voice",
                secondary = "On device speech models",
                onClick = onOpenVoice,
            )
            SettingsTransferRows(db, appSettings, appearance) { transferStatus = it }
            SectionLabel("About")
            val debug = context.isDebuggableApp()
            val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty() }
            ListRow(
                sh.simple.android.ui.kit.Icons.Term,
                "simple $version".trim(),
                chevron = false,
                modifier = if (debug) {
                    Modifier.combinedClickable(onClick = {}, onLongClick = onOpenKitGallery)
                } else {
                    Modifier
                },
                onClick = null,
            )
            ListRow(sh.simple.android.ui.kit.Icons.File, "Geist OFL", chevron = false, onClick = null)
            ListRow(sh.simple.android.ui.kit.Icons.File, "backdrop Apache 2.0", chevron = false, onClick = null)
        }
    }
}

@Composable
private fun AppearanceSliders(settings: AppearanceSettings, appearance: AppearanceController) {
    ColorThemeSection(settings, appearance)
    Toggle(checked = settings.liquidGlass, onChange = { on ->
        appearance.update { it.copy(liquidGlass = on) }
    }, modifier = SettingsRowPadding) {
        GlassToggleLabel(
            "Liquid glass",
            "Turns blur and lens on or off everywhere in the app.",
            "Cost: off skips all glass GPU work.",
        )
    }
    if (!settings.liquidGlass) return
    SectionLabel("Glass", Modifier.padding(start = 31.dp, top = 8.dp))
    GlassSlider(
        "Blur",
        settings.blur,
        0..40,
        { value -> appearance.update { it.copy(blur = value) } },
        SettingsRowPadding,
        infoWhat = "Softens what is behind the glass.",
        infoCost = "Cost: higher values use more GPU on every frame that moves.",
    )
    GlassSlider(
        "Tint",
        settings.tint,
        0..100,
        { value -> appearance.update { it.copy(tint = value) } },
        SettingsRowPadding,
        infoWhat = "Darkens the glass fill so less shows through.",
        infoCost = "Cost: mostly fill alpha, little extra GPU.",
    )
    val lensOk = Build.VERSION.SDK_INT >= 33
    GlassSlider(
        "Lens",
        settings.lensStrength,
        0..100,
        { value -> appearance.update { it.copy(lensStrength = value) } },
        SettingsRowPadding,
        enabled = lensOk,
        infoWhat = "Bends the edges like real glass.",
        infoCost = "Cost: extra GPU work, Android 13 and newer.",
    )
    if (!lensOk) {
        AppText(
            "Lens needs Android 13 or newer.",
            modifier = SettingsRowPadding.padding(bottom = 4.dp),
            style = Type.caption,
            color = Tokens.current.muted,
        )
    }
    Toggle(checked = settings.depthEffect, onChange = { on ->
        appearance.update { it.copy(depthEffect = on) }
    }, modifier = SettingsRowPadding) {
        GlassToggleLabel(
            "Depth effect",
            "Adds depth inside the lens refraction.",
            "Cost: extra GPU on Android 13 and newer.",
        )
    }
    Toggle(checked = settings.chromaticAberration, onChange = { on ->
        appearance.update { it.copy(chromaticAberration = on) }
    }, modifier = SettingsRowPadding) {
        GlassToggleLabel(
            "Chromatic aberration",
            "Splits colors at the glass rim like a prism.",
            "Cost: extra GPU on Android 13 and newer.",
        )
    }
    GlassFloatSlider(
        "Brightness",
        settings.brightness,
        -1f..1f,
        { value -> appearance.update { it.copy(brightness = value) } },
        SettingsRowPadding,
        infoWhat = "Brightens or dims the scene seen through the glass.",
        infoCost = "Cost: small color math each frame.",
    )
    GlassFloatSlider(
        "Contrast",
        settings.contrast,
        0f..2f,
        { value -> appearance.update { it.copy(contrast = value) } },
        SettingsRowPadding,
        infoWhat = "Strengthens or softens contrast behind the glass.",
        infoCost = "Cost: small color math each frame.",
    )
    GlassFloatSlider(
        "Saturation",
        settings.saturation,
        0f..2f,
        { value -> appearance.update { it.copy(saturation = value) } },
        SettingsRowPadding,
        infoWhat = "Boosts or mutes color behind the glass.",
        infoCost = "Cost: small color math each frame.",
    )
    Toggle(checked = settings.lightEdge, onChange = { on ->
        appearance.update { it.copy(lightEdge = on) }
    }, modifier = SettingsRowPadding) {
        GlassToggleLabel(
            "Light edge",
            "Draws a bright rim along the top of the glass.",
            "Cost: one thin highlight pass.",
        )
    }
    Toggle(checked = settings.glassShadow, onChange = { on ->
        appearance.update { it.copy(glassShadow = on) }
    }, modifier = SettingsRowPadding) {
        GlassToggleLabel(
            "Shadow",
            "Adds a soft drop shadow under floating glass.",
            "Cost: an extra shadow pass per surface.",
        )
    }
    ListRow(
        sh.simple.android.ui.kit.Icons.Refresh,
        "Reset to defaults",
        chevron = false,
        modifier = SettingsRowPadding,
    ) {
        appearance.update { AppearanceSettings.defaults.copy(colorScheme = settings.colorScheme, liquidGlass = settings.liquidGlass) }
    }
}

@Composable
private fun GlassToggleLabel(label: String, what: String, cost: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AppText(label, style = Type.body)
        SettingInfoTag(what, cost)
    }
}
