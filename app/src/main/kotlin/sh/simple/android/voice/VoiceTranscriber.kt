package sh.simple.android.voice

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object VoiceTranscriber {
    suspend fun transcribe(modelPath: String, pcm: FloatArray, modelId: String): String = withContext(Dispatchers.Default) {
        val language = whisperLanguage(modelId)
        val ctx = WhisperNative.loadModel(modelPath)
        if (ctx == 0L) return@withContext ""
        try {
            stripWhisperTags(WhisperNative.transcribe(ctx, pcm, threads = 4, language = language))
        } finally {
            WhisperNative.freeModel(ctx)
        }
    }

    fun whisperLanguage(modelId: String): String =
        if (modelId.endsWith(".en")) "en" else "auto"
}

private val whisperTag = Regex("""\[[^\]]*]|\((?:[^)]*\b(?:silence|music|noise|applause|laughter|inaudible|blank)\b[^)]*)\)|\*[^*]*\*""", RegexOption.IGNORE_CASE)

fun stripWhisperTags(text: String): String =
    whisperTag.replace(text, " ").replace(Regex("\\s+"), " ").trim()
