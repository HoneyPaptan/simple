package sh.simple.android.ui.composer

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ComposerTokensTest {
    @Test
    fun slashOnlyAtStart() {
        val field = TextFieldValue("/mod", TextRange(4))
        assertEquals("mod", activeComposerToken(field)?.query)
        val mid = TextFieldValue("see /mod", TextRange(8))
        assertNull(activeComposerToken(mid))
    }

    @Test
    fun fileMentionMidSentence() {
        val field = TextFieldValue("fix bug in @src/foo", TextRange(19))
        val token = activeComposerToken(field)!!
        assertEquals(ComposerTokenState.Kind.File, token.kind)
        assertEquals("src/foo", token.query)
        val replaced = applyComposerSuggestion(field, token, "src/main.kt")
        assertEquals("fix bug in @src/main.kt ", replaced.text)
        assertEquals(24, replaced.selection.start)
    }

    @Test
    fun caretInsideMiddleText() {
        val field = TextFieldValue("hello @app world", TextRange(10))
        val token = activeComposerToken(field)!!
        assertEquals("app", token.query)
    }

    @Test
    fun slashReplacement() {
        val field = TextFieldValue("/co", TextRange(3))
        val token = activeComposerToken(field)!!
        val replaced = applyComposerSuggestion(field, token, "compact")
        assertEquals("/compact ", replaced.text)
        assertEquals(9, replaced.selection.start)
    }
}
