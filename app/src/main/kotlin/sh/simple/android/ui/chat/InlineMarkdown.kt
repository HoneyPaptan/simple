package sh.simple.android.ui.chat

data class InlineSpan(
    val text: String,
    val code: Boolean = false,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val link: String? = null,
)

fun parseInline(source: String): List<InlineSpan> {
    val spans = mutableListOf<InlineSpan>()
    val plain = StringBuilder()
    fun flush() {
        if (plain.isNotEmpty()) {
            spans += InlineSpan(plain.toString())
            plain.clear()
        }
    }
    var i = 0
    while (i < source.length) {
        val c = source[i]
        if (c == '`') {
            val end = source.indexOf('`', i + 1)
            if (end > i + 1) {
                flush()
                spans += InlineSpan(source.substring(i + 1, end), code = true)
                i = end + 1
                continue
            }
        }
        if (source.startsWith("**", i) || source.startsWith("__", i)) {
            val mark = source.substring(i, i + 2)
            val end = source.indexOf(mark, i + 2)
            if (end > i + 2) {
                flush()
                parseInline(source.substring(i + 2, end)).forEach { spans += it.copy(bold = true) }
                i = end + 2
                continue
            }
        }
        if ((c == '*' || c == '_') && i + 1 < source.length && !source[i + 1].isWhitespace() && wordBoundaryBefore(source, i)) {
            val end = source.indexOf(c, i + 1)
            if (end > i + 1 && !source[end - 1].isWhitespace()) {
                flush()
                parseInline(source.substring(i + 1, end)).forEach { spans += it.copy(italic = true) }
                i = end + 1
                continue
            }
        }
        if (c == '[') {
            val close = source.indexOf("](", i + 1)
            val urlEnd = if (close > 0) source.indexOf(')', close + 2) else -1
            if (close > i + 1 && urlEnd > close + 2) {
                val url = source.substring(close + 2, urlEnd)
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    flush()
                    spans += InlineSpan(source.substring(i + 1, close), link = url)
                    i = urlEnd + 1
                    continue
                }
            }
        }
        plain.append(c)
        i++
    }
    flush()
    return spans
}

private fun wordBoundaryBefore(source: String, i: Int): Boolean =
    i == 0 || !source[i - 1].isLetterOrDigit()
