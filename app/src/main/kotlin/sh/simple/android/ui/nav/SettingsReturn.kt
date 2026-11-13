package sh.simple.android.ui.nav

fun shouldReopenSettingsAfterPop(popped: Screen, usageFromSettings: Boolean): Boolean = when (popped) {
    is Screen.Usage -> usageFromSettings
    is Screen.Webhooks -> true
    Screen.Voice, Screen.CodeFont, Screen.TerminalTheme, Screen.TerminalKeys, Screen.KitGallery -> true
    else -> false
}
