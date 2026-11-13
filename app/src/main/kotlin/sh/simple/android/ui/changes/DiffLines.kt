package sh.simple.android.ui.changes

data class DiffDisplayLine(val marker: Char, val text: String)

fun parseDiffForDisplay(raw: String): List<DiffDisplayLine> {
    val out = mutableListOf<DiffDisplayLine>()
    for (line in raw.lines()) {
        when {
            line.startsWith("diff --git") -> continue
            line.startsWith("index ") -> continue
            line.startsWith("--- ") -> continue
            line.startsWith("+++ ") -> continue
            line.startsWith("@@") -> out.add(DiffDisplayLine('@', line))
            line.startsWith("+") -> out.add(DiffDisplayLine('+', line.drop(1)))
            line.startsWith("-") -> out.add(DiffDisplayLine('-', line.drop(1)))
            line.startsWith(" ") -> out.add(DiffDisplayLine(' ', line.drop(1)))
            line.isEmpty() -> out.add(DiffDisplayLine(' ', ""))
            else -> out.add(DiffDisplayLine(' ', line))
        }
    }
    return out
}
