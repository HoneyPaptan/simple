package sh.simple.android.ui.pane

private val ellipsisNoise = Regex("^[·•.\\s…]+$")

fun formatPanePeekPreview(raw: String, maxLines: Int = 5): String {
    val lines = raw.lines()
        .map { it.trim() }
        .filter { line ->
            val t = line
            t.isNotEmpty() && t != "..." && t != "…" && !ellipsisNoise.matches(t)
        }
    return lines.takeLast(maxLines).joinToString("\n")
}
