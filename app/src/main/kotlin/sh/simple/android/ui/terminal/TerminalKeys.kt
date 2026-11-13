package sh.simple.android.ui.terminal

import android.view.KeyEvent
import com.termux.terminal.KeyHandler

fun terminalKeyBytes(
    keyCode: Int,
    event: KeyEvent,
    cursorKeysApplication: Boolean = false,
    keypadApplication: Boolean = false,
): ByteArray? {
    val mods = keyMods(event)
    val code = KeyHandler.getCode(keyCode, mods, cursorKeysApplication, keypadApplication) ?: return null
    return code.toByteArray(Charsets.UTF_8).filter { it != 0x1c.toByte() }.toByteArray()
}

fun keyMods(event: KeyEvent): Int {
    var mods = 0
    if (event.isCtrlPressed) mods = mods or KeyHandler.KEYMOD_CTRL
    if (event.isAltPressed) mods = mods or KeyHandler.KEYMOD_ALT
    if (event.isShiftPressed) mods = mods or KeyHandler.KEYMOD_SHIFT
    return mods
}

fun terminalKeySequence(
    keyCode: Int,
    event: KeyEvent,
    cursorKeysApplication: Boolean = false,
    keypadApplication: Boolean = false,
): String? {
    val bytes = terminalKeyBytes(keyCode, event, cursorKeysApplication, keypadApplication) ?: return null
    return bytes.decodeToString()
}

fun terminalBytesFromSpec(
    spec: String,
    cursorKeysApplication: Boolean = false,
    keypadApplication: Boolean = false,
): ByteArray {
    val s = spec.trim()
    if (s.isEmpty()) return byteArrayOf()
    val ctrl = Regex("(?i)ctrl\\+([a-zA-Z])").matchEntire(s)
    if (ctrl != null) {
        val c = ctrl.groupValues[1][0].lowercaseChar()
        return byteArrayOf((c.code - 'a'.code + 1).toByte())
    }
    if (s.equals("alt+enter", ignoreCase = true)) {
        return "\u001b\r".toByteArray(Charsets.UTF_8)
    }
    val keyCode = when (s.lowercase()) {
        "esc", "escape" -> KeyEvent.KEYCODE_ESCAPE
        "tab" -> KeyEvent.KEYCODE_TAB
        "up" -> KeyEvent.KEYCODE_DPAD_UP
        "down" -> KeyEvent.KEYCODE_DPAD_DOWN
        "left" -> KeyEvent.KEYCODE_DPAD_LEFT
        "right" -> KeyEvent.KEYCODE_DPAD_RIGHT
        "home" -> KeyEvent.KEYCODE_MOVE_HOME
        "end" -> KeyEvent.KEYCODE_MOVE_END
        "pageup" -> KeyEvent.KEYCODE_PAGE_UP
        "pagedown" -> KeyEvent.KEYCODE_PAGE_DOWN
        "enter" -> return "\r".toByteArray(Charsets.UTF_8)
        else -> return s.take(64).toByteArray(Charsets.UTF_8)
    }
    val event = KeyEvent(0, 0, KeyEvent.ACTION_DOWN, keyCode, 0)
    return terminalKeyBytes(keyCode, event, cursorKeysApplication, keypadApplication) ?: byteArrayOf()
}
