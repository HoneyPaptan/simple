package sh.simple.android.ui.terminal

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TerminalKeysTest {
  private fun down(keyCode: Int, meta: Int = 0): KeyEvent =
        KeyEvent(0, 0, KeyEvent.ACTION_DOWN, keyCode, 0, meta)

    @Test
    fun arrowKeysSendAnsi() {
        assertEquals("\u001b[A", terminalKeySequence(KeyEvent.KEYCODE_DPAD_UP, down(KeyEvent.KEYCODE_DPAD_UP)))
        assertEquals("\u001b[B", terminalKeySequence(KeyEvent.KEYCODE_DPAD_DOWN, down(KeyEvent.KEYCODE_DPAD_DOWN)))
        assertEquals("\u001b[D", terminalKeySequence(KeyEvent.KEYCODE_DPAD_LEFT, down(KeyEvent.KEYCODE_DPAD_LEFT)))
        assertEquals("\u001b[C", terminalKeySequence(KeyEvent.KEYCODE_DPAD_RIGHT, down(KeyEvent.KEYCODE_DPAD_RIGHT)))
    }

    @Test
    fun tabAndEscape() {
        assertEquals("\t", terminalKeySequence(KeyEvent.KEYCODE_TAB, down(KeyEvent.KEYCODE_TAB)))
        assertEquals("\u001b", terminalKeySequence(KeyEvent.KEYCODE_ESCAPE, down(KeyEvent.KEYCODE_ESCAPE)))
    }

    @Test
    fun navigationKeys() {
        assertEquals("\u001b[H", terminalKeySequence(KeyEvent.KEYCODE_MOVE_HOME, down(KeyEvent.KEYCODE_MOVE_HOME)))
        assertEquals("\u001b[F", terminalKeySequence(KeyEvent.KEYCODE_MOVE_END, down(KeyEvent.KEYCODE_MOVE_END)))
        assertNotNull(terminalKeySequence(KeyEvent.KEYCODE_PAGE_UP, down(KeyEvent.KEYCODE_PAGE_UP)))
        assertNotNull(terminalKeySequence(KeyEvent.KEYCODE_PAGE_DOWN, down(KeyEvent.KEYCODE_PAGE_DOWN)))
    }

    @Test
    fun functionKeys() {
        for (code in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12) {
            assertNotNull(terminalKeySequence(code, down(code)))
        }
    }

    @Test
    fun specParserHandlesCtrlAndText() {
        assertEquals(1, terminalBytesFromSpec("ctrl+c").size)
        assertEquals(0x03.toByte(), terminalBytesFromSpec("ctrl+c")[0])
        assertEquals(0x12.toByte(), terminalBytesFromSpec("Ctrl+R")[0])
        assertEquals("grep foo", terminalBytesFromSpec("grep foo").decodeToString())
        val altEnter = terminalBytesFromSpec("alt+enter")
        assertEquals(2, altEnter.size)
        assertEquals(0x1b.toByte(), altEnter[0])
        assertEquals(0x0d.toByte(), altEnter[1])
    }

}
