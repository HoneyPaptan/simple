package sh.simple.android.voice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import sh.simple.android.App

class DictationDebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION) return
        val path = intent.getStringExtra(EXTRA_WAV)?.trim().orEmpty()
        if (path.isEmpty()) return
        val app = context.applicationContext as App
        val pending = goAsync()
        app.appScope.launch(Dispatchers.IO) {
            try {
                runCatching {
                    if (!app.voiceModels.hasModel()) return@runCatching
                    val modelPath = app.voiceModels.localPath() ?: return@runCatching
                    val pcm = WavReader.loadMono16k(path)
                    if (pcm.isEmpty()) return@runCatching
                    val raw = VoiceTranscriber.transcribe(modelPath, pcm, app.voiceModels.selectedId)
                    val text = applyVoiceReplacements(raw, app.voiceReplacements.rules)
                    if (text.isBlank()) return@runCatching
                    app.voiceHistory.recordTranscript(text)
                    app.voiceHistory.queueInsert(text)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION = "sh.simple.android.debug.DICTATE"
        const val EXTRA_WAV = "wav"
    }
}
