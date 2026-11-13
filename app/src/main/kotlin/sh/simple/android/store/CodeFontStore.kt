package sh.simple.android.store

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class CodeFontChoice(
    val mode: String,
    val hostId: String = "",
    val hostPath: String = "",
    val label: String = "",
)

private val Context.codeFontStore: DataStore<Preferences> by preferencesDataStore("code_font")

private object CodeFontKeys {
    val mode = stringPreferencesKey("mode")
    val hostId = stringPreferencesKey("host_id")
    val hostPath = stringPreferencesKey("host_path")
    val label = stringPreferencesKey("label")
}

class CodeFontStore(context: Context, private val scope: CoroutineScope) {
    private val app = context.applicationContext
    private val store = app.codeFontStore
    var choice by mutableStateOf(CodeFontChoice("geist"))
        private set
    var loadNote by mutableStateOf("")
        private set

    val localFile: File
        get() = File(app.filesDir, "code-font/custom.ttf")

    init {
        scope.launch(Dispatchers.Main.immediate) {
            store.data.map { prefs ->
                CodeFontChoice(
                    mode = prefs[CodeFontKeys.mode] ?: "geist",
                    hostId = prefs[CodeFontKeys.hostId] ?: "",
                    hostPath = prefs[CodeFontKeys.hostPath] ?: "",
                    label = prefs[CodeFontKeys.label] ?: "",
                )
            }.flowOn(Dispatchers.IO).collect { choice = it }
        }
    }

    fun setGeist() {
        loadNote = ""
        localFile.parentFile?.mkdirs()
        localFile.delete()
        choice = CodeFontChoice("geist")
        scope.launch(Dispatchers.IO) {
            store.edit { prefs ->
                prefs[CodeFontKeys.mode] = "geist"
                prefs.remove(CodeFontKeys.hostId)
                prefs.remove(CodeFontKeys.hostPath)
                prefs.remove(CodeFontKeys.label)
            }
        }
    }

    fun setHostFont(hostId: String, hostPath: String, label: String, bytes: ByteArray) {
        val dir = localFile.parentFile ?: error("dir")
        dir.mkdirs()
        localFile.writeBytes(bytes)
        loadNote = ""
        choice = CodeFontChoice("host", hostId, hostPath, label)
        scope.launch(Dispatchers.IO) {
            store.edit { prefs ->
                prefs[CodeFontKeys.mode] = "host"
                prefs[CodeFontKeys.hostId] = hostId
                prefs[CodeFontKeys.hostPath] = hostPath
                prefs[CodeFontKeys.label] = label
            }
        }
    }

    fun noteLoadFailed() {
        loadNote = "Could not load that font. Using Geist Mono."
        localFile.delete()
        choice = CodeFontChoice("geist")
        scope.launch(Dispatchers.IO) {
            store.edit { prefs ->
                prefs[CodeFontKeys.mode] = "geist"
                prefs.remove(CodeFontKeys.hostId)
                prefs.remove(CodeFontKeys.hostPath)
                prefs.remove(CodeFontKeys.label)
            }
        }
    }
}
