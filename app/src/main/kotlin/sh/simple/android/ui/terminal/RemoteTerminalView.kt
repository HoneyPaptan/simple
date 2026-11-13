package sh.simple.android.ui.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PersistableBundle
import android.widget.Toast
import android.graphics.Canvas
import android.graphics.Typeface
import android.text.InputType
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import com.termux.terminal.KeyHandler
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalOutput
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalRenderer
import kotlin.math.max
import kotlin.math.roundToInt

private const val maxRemoteCopy = 100_000

private const val TRANSCRIPT_ROWS = 2000
private const val MIN_TEXT_SP = 8f
private const val MAX_TEXT_SP = 24f
private const val PREFS = "terminal"
private const val PREF_TEXT_SP = "text_sp"
private val urlPattern = Regex("""(https?|file)://[^\s<>"'`]+""")

class RemoteTerminalView(context: Context) : View(context) {
    var onInput: (ByteArray) -> Unit = {}
    var onGridSize: (cols: Int, rows: Int) -> Unit = { _, _ -> }
    var ctrlArmed: () -> Boolean = { false }
    var consumeCtrl: () -> Unit = {}
    private var palette: TerminalPalette? = null

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private var textSp = prefs.getFloat(PREF_TEXT_SP, 13f).coerceIn(MIN_TEXT_SP, MAX_TEXT_SP)
    private var terminalTypeface = Typeface.MONOSPACE
    private var renderer = TerminalRenderer(textSizePx(textSp), terminalTypeface)
    private var scaling = false
    private var emulator: TerminalEmulator? = null
    private var topRow = 0
    private var scrollRemainder = 0f

    private val output = object : TerminalOutput() {
        override fun write(data: ByteArray, offset: Int, count: Int) = onInput(data.copyOfRange(offset, offset + count))
        override fun titleChanged(oldTitle: String?, newTitle: String?) = Unit
        override fun onCopyTextToClipboard(text: String?) {
            if (!text.isNullOrEmpty() && text.length <= maxRemoteCopy) post {
                copyToClipboard(text)
                Toast.makeText(context, "Copied from terminal", Toast.LENGTH_SHORT).show()
            }
        }
        override fun onPasteTextFromClipboard() = Unit
        override fun onBell() = Unit
        override fun onColorsChanged() = invalidate()
    }

    private val gestures = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                val url = urlAt(e.x, e.y)
                if (url != null) openUrl(url) else showKeyboard()
                return true
            }

            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
                if (!scaling) scrollBy(dy)
                return true
            }
        },
    )

    private val scaler = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                scaling = true
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                setTextSp(textSp * detector.scaleFactor)
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                prefs.edit().putFloat(PREF_TEXT_SP, textSp).apply()
            }
        },
    )

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    val gridCols: Int get() = emulator?.mColumns ?: 0
    val gridRows: Int get() = emulator?.mRows ?: 0

    fun feed(bytes: ByteArray, full: Boolean = false) {
        if (full) {
            recreateEmulator()
        }
        val emu = emulator ?: return
        emu.append(bytes, bytes.size)
        topRow = 0
        invalidate()
    }

    fun reset() {
        emulator = null
        topRow = 0
        recomputeGrid(width, height)
    }

    private fun recreateEmulator() {
        val emu = emulator
        if (emu == null) {
            topRow = 0
            return
        }
        val cols = emu.mColumns
        val rows = emu.mRows
        emulator = TerminalEmulator(output, cols, rows, TRANSCRIPT_ROWS, NoopSessionClient).also { created ->
            palette?.applyTo(created)
        }
        topRow = 0
    }

    fun applyPalette(next: TerminalPalette) {
        palette = next
        emulator?.let {
            next.applyTo(it)
            output.onColorsChanged()
        }
        invalidate()
    }

    fun setTerminalTypeface(tf: Typeface) {
        if (tf == terminalTypeface) return
        terminalTypeface = tf
        renderer = TerminalRenderer(textSizePx(textSp), terminalTypeface)
        recomputeGrid(width, height)
        invalidate()
    }

    fun showKeyboard() {
        requestFocus()
        context.getSystemService(InputMethodManager::class.java)?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
    }

    fun sendKey(keyCode: Int, mods: Int = 0) {
        val emu = emulator
        val code = KeyHandler.getCode(
            keyCode,
            mods,
            emu?.isCursorKeysApplicationMode ?: false,
            emu?.isKeypadApplicationMode ?: false,
        ) ?: return
        sendText(code)
    }

    fun sendKeyEvent(event: KeyEvent) {
        val emu = emulator
        val bytes = terminalKeyBytes(
            event.keyCode,
            event,
            emu?.isCursorKeysApplicationMode ?: false,
            emu?.isKeypadApplicationMode ?: false,
        ) ?: return
        if (bytes.isNotEmpty()) onInput(bytes)
    }

    fun paste() {
        val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip ?: return
        if (clip.itemCount == 0) return
        val text = clip.getItemAt(0).coerceToText(context)?.toString().orEmpty()
        if (text.isNotEmpty()) emulator?.paste(text)
    }

    fun copyScreen() {
        val emu = emulator ?: return
        val text = emu.screen.getSelectedText(0, topRow, emu.mColumns - 1, topRow + emu.mRows - 1).trimEnd()
        if (text.isNotEmpty()) copyToClipboard(text)
    }

    private fun copyToClipboard(text: String) {
        val clip = ClipData.newPlainText("terminal", text)
        clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
    }

    private fun setTextSp(sp: Float) {
        val next = sp.coerceIn(MIN_TEXT_SP, MAX_TEXT_SP)
        if (kotlin.math.abs(next - textSp) < 0.25f) return
        textSp = next
        renderer = TerminalRenderer(textSizePx(textSp), terminalTypeface)
        recomputeGrid(width, height)
        invalidate()
    }

    private fun urlAt(x: Float, y: Float): String? {
        val emu = emulator ?: return null
        val row = topRow + (y / renderer.fontLineSpacing).toInt()
        val col = (x / renderer.fontWidth).toInt()
        if (row >= emu.mRows || row < -emu.screen.activeTranscriptRows) return null
        val line = emu.screen.getSelectedText(0, row, emu.mColumns - 1, row)
        return urlPattern.findAll(line).firstOrNull { col in it.range }?.value?.trimEnd('.', ',', ')', ']', ';', ':')
    }

    private fun openUrl(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun sendText(text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8).filter { it != CTRL_BACKSLASH }.toByteArray()
        if (bytes.isNotEmpty()) onInput(bytes)
    }

    fun sendBarSpec(spec: String) {
        val emu = emulator
        val bytes = terminalBytesFromSpec(
            spec,
            emu?.isCursorKeysApplicationMode ?: false,
            emu?.isKeypadApplicationMode ?: false,
        )
        if (bytes.isNotEmpty()) onInput(bytes)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        recomputeGrid(w, h)
    }

    private fun recomputeGrid(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        val cols = max(4, (w / renderer.fontWidth).toInt())
        val rows = max(4, h / renderer.fontLineSpacing)
        val emu = emulator
        if (emu == null) {
            emulator = TerminalEmulator(output, cols, rows, TRANSCRIPT_ROWS, NoopSessionClient).also { created ->
                palette?.applyTo(created)
            }
        } else if (emu.mColumns != cols || emu.mRows != rows) {
            emu.resize(cols, rows)
        } else {
            return
        }
        onGridSize(cols, rows)
        invalidate()
    }

    private fun scrollBy(dy: Float) {
        val emu = emulator ?: return
        scrollRemainder += dy
        val lines = (scrollRemainder / renderer.fontLineSpacing).toInt()
        if (lines == 0) return
        scrollRemainder -= lines * renderer.fontLineSpacing
        if (emu.isAlternateBufferActive) {
            repeat(kotlin.math.abs(lines)) { sendKey(if (lines < 0) KeyEvent.KEYCODE_DPAD_UP else KeyEvent.KEYCODE_DPAD_DOWN) }
            return
        }
        topRow = (topRow + lines).coerceIn(-emu.screen.activeTranscriptRows, 0)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val emu = emulator ?: return
        renderer.render(emu, canvas, topRow, -1, -1, -1, -1)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaler.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_DOWN) scaling = false
        gestures.onTouchEvent(event)
        return true
    }

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        return object : BaseInputConnection(this, true) {
            override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
                typeText(text.toString())
                editable?.clear()
                return true
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                repeat(max(1, beforeLength)) { sendText("\u007f") }
                return true
            }

            override fun sendKeyEvent(event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_DOWN) onKeyDown(event.keyCode, event)
                return true
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_BACK -> return super.onKeyDown(keyCode, event)
        }
        if (keyCode == KeyEvent.KEYCODE_ENTER && !event.isCtrlPressed && !event.isAltPressed) {
            sendText("\r")
            return true
        }
        val unicode = event.unicodeChar
        if (unicode != 0 && !event.isCtrlPressed && !event.isAltPressed && !isSpecialKey(keyCode)) {
            typeText(String(Character.toChars(unicode)))
            return true
        }
        sendKeyEvent(event)
        return true
    }

    private fun isSpecialKey(keyCode: Int): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_TAB,
        KeyEvent.KEYCODE_ESCAPE,
        KeyEvent.KEYCODE_FORWARD_DEL,
        KeyEvent.KEYCODE_DEL,
        KeyEvent.KEYCODE_INSERT,
        KeyEvent.KEYCODE_MOVE_HOME,
        KeyEvent.KEYCODE_MOVE_END,
        KeyEvent.KEYCODE_PAGE_UP,
        KeyEvent.KEYCODE_PAGE_DOWN,
        in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12,
        in KeyEvent.KEYCODE_DPAD_UP..KeyEvent.KEYCODE_DPAD_RIGHT,
        -> true
        else -> false
    }

    private fun typeText(text: String) {
        if (text.isEmpty()) return
        if (ctrlArmed()) {
            consumeCtrl()
            val first = text[0]
            ctrlByte(first)?.let { sendText(it.toString()) }
            sendText(text.substring(1).replace('\n', '\r'))
            return
        }
        sendText(text.replace('\n', '\r'))
    }

    private fun textSizePx(sp: Float): Int = (sp * resources.displayMetrics.scaledDensity).roundToInt()
}

private const val CTRL_BACKSLASH: Byte = 0x1c

fun ctrlByte(c: Char): Char? {
    val lower = c.lowercaseChar()
    return when {
        lower in 'a'..'z' -> (lower.code - 'a'.code + 1).toChar()
        c == '[' -> '\u001b'
        c == ' ' || c == '@' -> '\u0000'
        else -> null
    }
}

private object NoopSessionClient : TerminalSessionClient {
    override fun onTextChanged(changedSession: TerminalSession) = Unit
    override fun onTitleChanged(changedSession: TerminalSession) = Unit
    override fun onSessionFinished(finishedSession: TerminalSession) = Unit
    override fun onCopyTextToClipboard(session: TerminalSession, text: String?) = Unit
    override fun onPasteTextFromClipboard(session: TerminalSession?) = Unit
    override fun onBell(session: TerminalSession) = Unit
    override fun onColorsChanged(session: TerminalSession) = Unit
    override fun onTerminalCursorStateChange(state: Boolean) = Unit
    override fun getTerminalCursorStyle(): Int = 0
    override fun logError(tag: String?, message: String?) = Unit
    override fun logWarn(tag: String?, message: String?) = Unit
    override fun logInfo(tag: String?, message: String?) = Unit
    override fun logDebug(tag: String?, message: String?) = Unit
    override fun logVerbose(tag: String?, message: String?) = Unit
    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) = Unit
    override fun logStackTrace(tag: String?, e: Exception?) = Unit
}
