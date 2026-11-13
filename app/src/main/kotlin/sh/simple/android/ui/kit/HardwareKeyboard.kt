package sh.simple.android.ui.kit

import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.runtime.LaunchedEffect
import android.content.res.Configuration
import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration

@Composable
fun rememberHardwareKeyboardAttached(): Boolean {
    val config = LocalConfiguration.current
    return remember(config.keyboard, config.hardKeyboardHidden) {
        config.keyboard != Configuration.KEYBOARD_NOKEYS &&
            config.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO
    }
}

fun keyEventIsCtrlBracketLeft(event: KeyEvent): Boolean =
    event.isCtrlPressed && event.keyCode == KeyEvent.KEYCODE_LEFT_BRACKET

fun keyEventIsCtrlBracketRight(event: KeyEvent): Boolean =
    event.isCtrlPressed && event.keyCode == KeyEvent.KEYCODE_RIGHT_BRACKET

fun keyEventIsCtrlLetter(event: KeyEvent, letterKeyCode: Int): Boolean =
    event.isCtrlPressed && !event.isAltPressed && event.keyCode == letterKeyCode

fun composeKeyIsCtrlBracketLeft(native: androidx.compose.ui.input.key.KeyEvent): Boolean =
    native.isCtrlPressed && native.key == Key.LeftBracket

fun composeKeyIsCtrlBracketRight(native: androidx.compose.ui.input.key.KeyEvent): Boolean =
    native.isCtrlPressed && native.key == Key.RightBracket

fun composeKeyIsCtrlNative(native: androidx.compose.ui.input.key.KeyEvent, androidKeyCode: Int): Boolean {
    val match = when (androidKeyCode) {
        KeyEvent.KEYCODE_K -> Key.K
        KeyEvent.KEYCODE_N -> Key.N
        else -> return false
    }
    return native.isCtrlPressed && native.key == match
}

fun Modifier.homeKeyboardShortcuts(
    enabled: Boolean,
    onSearch: () -> Unit,
    onNewAgent: () -> Unit,
): Modifier {
    if (!enabled) return this
    return then(
        Modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when {
                composeKeyIsCtrlNative(event, KeyEvent.KEYCODE_K) -> {
                    onSearch()
                    true
                }
                composeKeyIsCtrlNative(event, KeyEvent.KEYCODE_N) -> {
                    onNewAgent()
                    true
                }
                else -> false
            }
        },
    )
}

fun Modifier.agentNeighborKeyboardShortcuts(
    enabled: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
): Modifier {
    if (!enabled) return this
    return then(
        Modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when {
                composeKeyIsCtrlBracketLeft(event) -> {
                    onPrev()
                    true
                }
                composeKeyIsCtrlBracketRight(event) -> {
                    onNext()
                    true
                }
                else -> false
            }
        },
    )
}

@Composable
fun rememberShortcutFocus(refocusKey: Any?): Modifier {
    val requester = remember { FocusRequester() }
    LaunchedEffect(refocusKey) { runCatching { requester.requestFocus() } }
    return Modifier.focusRequester(requester).focusable()
}
