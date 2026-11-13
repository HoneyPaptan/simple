package sh.simple.android.voice

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class VoiceHistoryEntry(val id: String, val text: String, val createdMs: Long)

@Serializable
private data class VoiceHistoryRow(val id: String, val text: String, val created_ms: Long)

private val Context.voiceHistoryStore by preferencesDataStore("voice_history")

private object VoiceHistoryKeys {
    val json = stringPreferencesKey("entries_json")
}

class VoiceHistoryStore(
    context: Context,
    private val scope: CoroutineScope,
) {
    private val app = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }

    var entries by mutableStateOf<List<VoiceHistoryEntry>>(emptyList())
        private set

    var pendingInsert by mutableStateOf<String?>(null)
        private set

    init {
        scope.launch(Dispatchers.Main.immediate) {
            app.voiceHistoryStore.data.map { prefs ->
                decode(prefs[VoiceHistoryKeys.json].orEmpty())
            }.flowOn(Dispatchers.IO).collect { entries = it }
        }
    }

    fun recordTranscript(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val entry = VoiceHistoryEntry(UUID.randomUUID().toString(), trimmed, System.currentTimeMillis())
        val next = (listOf(entry) + entries).take(20)
        persist(next)
    }

    fun delete(id: String) {
        persist(entries.filter { it.id != id })
    }

    fun clearAll() {
        persist(emptyList())
    }

    fun queueInsert(text: String) {
        pendingInsert = text
    }

    fun consumePendingInsert(): String? {
        val t = pendingInsert
        pendingInsert = null
        return t
    }

    private fun persist(next: List<VoiceHistoryEntry>) {
        entries = next
        scope.launch(Dispatchers.IO) {
            app.voiceHistoryStore.edit { prefs ->
                prefs[VoiceHistoryKeys.json] = encode(next)
            }
        }
    }

    private fun encode(list: List<VoiceHistoryEntry>): String {
        val rows = list.map { VoiceHistoryRow(it.id, it.text, it.createdMs) }
        return json.encodeToString(rows)
    }

    private fun decode(raw: String): List<VoiceHistoryEntry> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            json.decodeFromString<List<VoiceHistoryRow>>(raw).map {
                VoiceHistoryEntry(it.id, it.text, it.created_ms)
            }
        }.getOrDefault(emptyList())
    }
}
