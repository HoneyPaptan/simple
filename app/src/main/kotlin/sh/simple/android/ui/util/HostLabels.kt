package sh.simple.android.ui.util

fun showPairedComputerNames(pairedHostCount: Int): Boolean = pairedHostCount > 1

fun optionalComputerName(hostId: String, hostNames: Map<String, String>, showComputer: Boolean): String? {
    if (!showComputer) return null
    return hostNames[hostId] ?: hostId
}

fun joinMetaParts(parts: List<String?>): String = parts.filterNotNull().filter { it.isNotEmpty() }.joinToString(" · ")
