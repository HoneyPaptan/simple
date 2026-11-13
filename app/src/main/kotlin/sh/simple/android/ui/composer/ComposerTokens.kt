package sh.simple.android.ui.composer

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

data class ComposerTokenState(
    val kind: Kind,
    val start: Int,
    val end: Int,
    val query: String,
) {
    enum class Kind { Slash, File }
}

fun activeComposerToken(field: TextFieldValue): ComposerTokenState? {
    val text = field.text
    val caret = field.selection.start.coerceIn(0, text.length)
    if (text.isEmpty() || caret == 0) return null
    val before = text.substring(0, caret)
    val at = before.lastIndexOf('@')
    if (at >= 0) {
        val tail = before.substring(at + 1)
        if (!tail.contains(' ') && !tail.contains('\n')) {
            return ComposerTokenState(ComposerTokenState.Kind.File, at, caret, tail)
        }
    }
    if (!text.startsWith('/')) return null
    val slashEnd = text.indexOfFirst { it == ' ' || it == '\n' }.let { if (it < 0) text.length else it }
    if (caret > slashEnd) return null
    val query = text.substring(1, caret)
    if (query.contains(' ') || query.contains('\n')) return null
    return ComposerTokenState(ComposerTokenState.Kind.Slash, 0, caret, query)
}

fun applyComposerSuggestion(field: TextFieldValue, state: ComposerTokenState, value: String): TextFieldValue {
    val text = field.text
    val replacement = when (state.kind) {
        ComposerTokenState.Kind.Slash -> "/$value "
        ComposerTokenState.Kind.File -> "@$value "
    }
    val newText = text.substring(0, state.start) + replacement + text.substring(state.end)
    val caret = state.start + replacement.length
    return field.copy(text = newText, selection = TextRange(caret))
}

fun fileSuggestionLabels(path: String): Pair<String, String> {
    val normalized = path.replace('\\', '/')
    val slash = normalized.lastIndexOf('/')
    if (slash < 0) return normalized to ""
    val base = normalized.substring(slash + 1)
    val folder = normalized.substring(0, slash)
    return base to folder
}
