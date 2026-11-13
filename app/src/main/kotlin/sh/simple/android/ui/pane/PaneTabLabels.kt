package sh.simple.android.ui.pane

import sh.simple.android.model.PaneTab

private val tabIndexPrefix = Regex("^\\d+\\s*·\\s*")
private val tabTitleSplit = Regex("\\s*[›>]\\s*")

fun herdrTabChipLabels(tabs: List<PaneTab>): List<String> {
    if (tabs.isEmpty()) return emptyList()
    val titles = tabs.map { cleanHerdrTabTitle(it.label) }
    val dupes = titles.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
    return tabs.mapIndexed { index, tab ->
        val title = cleanHerdrTabTitle(tab.label)
        if (title in dupes) {
            val num = tabNumericPrefix(tab.label) ?: (index + 1).toString()
            "$num · $title"
        } else {
            title
        }
    }
}

fun cleanHerdrTabTitle(label: String): String {
    var s = label.trim()
    if (s.isEmpty()) return "Tab"
    s = tabIndexPrefix.replace(s, "")
    val parts = s.split(tabTitleSplit, limit = 2)
    return if (parts.size == 2 && parts[1].isNotBlank()) parts[1].trim() else s.trim()
}

private fun tabNumericPrefix(label: String): String? =
    Regex("^(\\d+)\\s*·").find(label.trim())?.groupValues?.getOrNull(1)
