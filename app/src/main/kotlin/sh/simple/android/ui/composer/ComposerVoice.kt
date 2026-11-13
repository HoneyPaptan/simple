package sh.simple.android.ui.composer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.simple.android.voice.DictationAudio
import sh.simple.android.voice.VoiceModelStore
import sh.simple.android.voice.VoiceTranscriber
import sh.simple.android.voice.applyVoiceReplacements

sealed interface ComposerVoicePhase {
    data object Idle : ComposerVoicePhase
    data class Recording(val level: Float, val elapsedSec: Int) : ComposerVoicePhase
    data object Transcribing : ComposerVoicePhase
}

class ComposerVoiceHandle(
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val voiceStore: VoiceModelStore,
    private val replacements: sh.simple.android.voice.VoiceReplacementsStore,
    private val history: sh.simple.android.voice.VoiceHistoryStore,
    private val onOpenVoiceSettings: () -> Unit,
    private val requestPermission: () -> Unit,
    private val hasPermission: () -> Boolean,
) {
    var phase by mutableStateOf<ComposerVoicePhase>(ComposerVoicePhase.Idle)
    var permissionHint by mutableStateOf("")
    var transcriptReady by mutableStateOf<String?>(null)
        private set

    private var recordJob: Job? = null
    private var stopCapture = false

    fun consumeTranscript(): String? {
        val t = transcriptReady
        transcriptReady = null
        return t
    }

    fun onMic(context: Context) {
        when (phase) {
            is ComposerVoicePhase.Recording -> stopCapture = true
            ComposerVoicePhase.Transcribing -> return
            ComposerVoicePhase.Idle -> startIfReady(context)
        }
    }

    fun cancel() {
        stopCapture = true
        recordJob?.cancel()
        recordJob = null
        stopCapture = false
        phase = ComposerVoicePhase.Idle
    }

    private fun startIfReady(context: Context) {
        if (!voiceStore.hasModel()) {
            onOpenVoiceSettings()
            return
        }
        if (!hasPermission()) {
            requestPermission()
            return
        }
        permissionHint = ""
        val path = voiceStore.localPath() ?: run {
            onOpenVoiceSettings()
            return
        }
        val modelId = voiceStore.selectedId
        recordJob?.cancel()
        recordJob = null
        stopCapture = false
        phase = ComposerVoicePhase.Recording(0f, 0)
        recordJob = scope.launch(Dispatchers.IO) {
            val record = DictationAudio.createRecord(context)
            val shorts = ShortArray(4096)
            val chunks = ArrayList<Float>(DictationAudio.maxSamples())
            val maxSamples = DictationAudio.maxSamples()
            record.startRecording()
            try {
                while (isActive && !stopCapture && chunks.size < maxSamples) {
                    val n = record.read(shorts, 0, shorts.size)
                    if (n > 0) {
                        val level = DictationAudio.levelFromPcm(shorts, n)
                        val floats = DictationAudio.pcm16ToFloat(shorts, n)
                        for (f in floats) {
                            if (chunks.size >= maxSamples) break
                            chunks.add(f)
                        }
                        val sec = chunks.size / DictationAudio.SAMPLE_RATE
                        withContext(Dispatchers.Main) {
                            phase = ComposerVoicePhase.Recording(level, sec)
                        }
                    }
                }
            } finally {
                record.stop()
                record.release()
            }
            if (!isActive) return@launch
            val pcm = chunks.toFloatArray()
            withContext(Dispatchers.Main) { phase = ComposerVoicePhase.Transcribing }
            val raw = VoiceTranscriber.transcribe(path, pcm, modelId)
            val text = applyVoiceReplacements(raw, replacements.rules)
            withContext(Dispatchers.Main) {
                phase = ComposerVoicePhase.Idle
                if (text.isNotBlank()) {
                    history.recordTranscript(text)
                } else {
                    permissionHint = "Didn't catch any words. Try again a little closer to the mic."
                }
                transcriptReady = text
            }
        }
    }
}

fun insertTranscript(value: TextFieldValue, words: String): TextFieldValue {
    if (words.isEmpty()) return value
    val sel = value.selection
    val start = sel.min.coerceIn(0, value.text.length)
    val end = sel.max.coerceIn(0, value.text.length)
    val prefix = if (start > 0 && !value.text[start - 1].isWhitespace()) " " else ""
    val chunk = prefix + words
    val newText = buildString {
        append(value.text.substring(0, start))
        append(chunk)
        append(value.text.substring(end))
    }
    val cursor = start + chunk.length
    return TextFieldValue(newText, TextRange(cursor))
}

@Composable
fun rememberComposerVoice(
    voiceStore: VoiceModelStore,
    replacements: sh.simple.android.voice.VoiceReplacementsStore,
    history: sh.simple.android.voice.VoiceHistoryStore,
    onOpenVoiceSettings: () -> Unit,
): ComposerVoiceHandle {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val handleRef = remember { mutableStateOf<ComposerVoiceHandle?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionGranted = granted
        if (granted) {
            handleRef.value?.onMic(context)
        } else {
            handleRef.value?.permissionHint = "Voice needs the microphone. Turn on microphone access for simple in your phone settings."
        }
    }
    val handle = remember(voiceStore, replacements, history, onOpenVoiceSettings, scope) {
        ComposerVoiceHandle(
            scope = scope,
            voiceStore = voiceStore,
            replacements = replacements,
            history = history,
            onOpenVoiceSettings = onOpenVoiceSettings,
            requestPermission = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
            hasPermission = { permissionGranted },
        )
    }
    handleRef.value = handle
    return handle
}
