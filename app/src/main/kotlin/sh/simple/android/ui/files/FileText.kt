package sh.simple.android.ui.files

fun textForWrite(original: String, edited: String): String {
    val hadTrailingNewline = original.endsWith("\n")
    var out = edited
    while (out.endsWith("\n")) {
        out = out.dropLast(1)
    }
    return if (hadTrailingNewline) "$out\n" else out
}

fun viewerLines(text: String): List<String> = text.removeSuffix("\n").lines()
