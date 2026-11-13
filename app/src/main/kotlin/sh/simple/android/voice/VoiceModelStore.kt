package sh.simple.android.voice

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import sh.simple.android.link.voiceDownloadError
import okhttp3.Request

sealed interface VoiceDownloadState {
    data object Idle : VoiceDownloadState
    data class Running(val received: Long, val total: Long) : VoiceDownloadState
    data object Ready : VoiceDownloadState
    data class Failed(val reason: String) : VoiceDownloadState
}

private val Context.voiceStore by preferencesDataStore("voice_models")

private object VoiceKeys {
    val selected = stringPreferencesKey("selected_model")
}

class VoiceModelStore(
    context: Context,
    private val scope: CoroutineScope,
    private val client: OkHttpClient = OkHttpClient(),
) {
    private val app = context.applicationContext
    private val dir = File(app.filesDir, "voice-models").apply { mkdirs() }
    private val jobs = mutableMapOf<String, Job>()
    private val calls = mutableMapOf<String, AtomicReference<okhttp3.Call?>>()

    var selectedId by mutableStateOf(VoiceCatalog.DEFAULT_ID)
        private set

    val downloadStates = mutableStateMapOf<String, VoiceDownloadState>()

    init {
        for (spec in VoiceCatalog.models) {
            downloadStates[spec.id] = if (modelFile(spec).isFile) VoiceDownloadState.Ready else VoiceDownloadState.Idle
        }
        scope.launch(Dispatchers.Main.immediate) {
            app.voiceStore.data.map { prefs ->
                prefs[VoiceKeys.selected] ?: VoiceCatalog.DEFAULT_ID
            }.flowOn(Dispatchers.IO).collect { selectedId = it }
        }
    }

    fun modelFile(spec: VoiceModelSpec): File = File(dir, spec.fileName)

    fun localPath(id: String = selectedId): String? {
        val spec = VoiceCatalog.find(id) ?: return null
        val file = modelFile(spec)
        return if (file.isFile) file.absolutePath else null
    }

    fun hasModel(id: String = selectedId): Boolean {
        val spec = VoiceCatalog.find(id) ?: return false
        return modelFile(spec).isFile && downloadStates[id] is VoiceDownloadState.Ready
    }

    fun setSelected(id: String) {
        if (VoiceCatalog.find(id) == null) return
        selectedId = id
        scope.launch(Dispatchers.IO) {
            app.voiceStore.edit { prefs -> prefs[VoiceKeys.selected] = id }
        }
    }

    fun refreshReadyFlags() {
        for (spec in VoiceCatalog.models) {
            if (modelFile(spec).isFile) {
                downloadStates[spec.id] = VoiceDownloadState.Ready
            }
        }
    }

    fun cancelDownload(id: String) {
        jobs.remove(id)?.cancel()
        calls[id]?.getAndSet(null)?.cancel()
        val spec = VoiceCatalog.find(id) ?: return
        if (!modelFile(spec).isFile) {
            downloadStates[id] = VoiceDownloadState.Idle
        }
    }

    fun deleteModel(id: String) {
        cancelDownload(id)
        VoiceCatalog.find(id)?.let { spec ->
            modelFile(spec).delete()
            downloadStates[id] = VoiceDownloadState.Idle
        }
    }

    fun startDownload(id: String, allowCellular: Boolean, onNeedCellularConfirm: () -> Unit) {
        val spec = VoiceCatalog.find(id) ?: return
        if (modelFile(spec).isFile) {
            downloadStates[id] = VoiceDownloadState.Ready
            return
        }
        if (!allowCellular && !app.onWifiOrEthernet()) {
            onNeedCellularConfirm()
            return
        }
        cancelDownload(id)
        downloadStates[id] = VoiceDownloadState.Running(0, spec.bytes)
        val callRef = AtomicReference<okhttp3.Call?>(null)
        calls[id] = callRef
        jobs[id] = scope.launch(Dispatchers.IO) {
            runCatching { downloadFile(spec, callRef) }
                .onSuccess {
                    if (verifySha256(spec)) {
                        downloadStates[id] = VoiceDownloadState.Ready
                    } else {
                        modelFile(spec).delete()
                        downloadStates[id] = VoiceDownloadState.Failed("Hash check failed, file removed")
                    }
                }
                .onFailure { e ->
                    modelFile(spec).delete()
                    if (e is kotlinx.coroutines.CancellationException) {
                        downloadStates[id] = VoiceDownloadState.Idle
                    } else {
                        downloadStates[id] = VoiceDownloadState.Failed(voiceDownloadError(e))
                    }
                }
        }
    }

    private fun verifySha256(spec: VoiceModelSpec): Boolean {
        val file = modelFile(spec)
        if (!file.isFile) return false
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(8192)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                digest.update(buf, 0, n)
            }
        }
        val hex = digest.digest().joinToString("") { b -> "%02x".format(b) }
        return hex.equals(spec.sha256, ignoreCase = true)
    }

    private suspend fun downloadFile(spec: VoiceModelSpec, callRef: AtomicReference<okhttp3.Call?>) {
        val dest = modelFile(spec)
        val tmp = File(dir, "${spec.fileName}.part")
        tmp.delete()
        val request = Request.Builder().url(VoiceCatalog.downloadUrl(spec)).build()
        val call = client.newCall(request)
        callRef.set(call)
        val response = call.execute()
        if (!response.isSuccessful) {
            response.close()
            error("HTTP ${response.code}")
        }
        val body = response.body ?: error("empty body")
        val total = body.contentLength().takeIf { it > 0 } ?: spec.bytes
        body.byteStream().use { input ->
            tmp.outputStream().use { out ->
                val buf = ByteArray(8192)
                var received = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    out.write(buf, 0, n)
                    received += n
                    withContext(Dispatchers.Main) {
                        downloadStates[spec.id] = VoiceDownloadState.Running(received, total)
                    }
                }
            }
        }
        response.close()
        if (!tmp.renameTo(dest)) {
            tmp.delete()
            error("Could not save model")
        }
    }
}
