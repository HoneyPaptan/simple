package sh.simple.android.link

fun shouldRunLinkForegroundService(
    backgroundAlerts: Boolean,
    pairedHostCount: Int,
    openItemCount: Int,
    appInForeground: Boolean,
): Boolean {
    if (appInForeground) return false
    if (backgroundAlerts && pairedHostCount > 0) return true
    if (openItemCount > 0) return true
    return false
}
