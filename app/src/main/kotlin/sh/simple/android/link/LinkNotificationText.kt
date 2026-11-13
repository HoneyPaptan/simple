package sh.simple.android.link

fun linkForegroundNotificationText(rows: List<HostConnectionRow>): String {
    if (rows.isEmpty()) return "Keeping host connections alive"
    if (rows.all { it.state is HostLinkState.Live }) {
        val n = rows.size
        return if (n == 1) "Connected to 1 computer" else "Connected to $n computers"
    }
    val target = rows.first { it.state !is HostLinkState.Live }
    if (target.state is HostLinkState.Revoked) return "Pair again with ${target.name}"
    return "Reconnecting to ${target.name}"
}
