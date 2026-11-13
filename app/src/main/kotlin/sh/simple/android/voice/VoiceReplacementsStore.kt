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

@Serializable
private data class VoiceReplacementRow(val id: String, val heard: String, val write: String)

private val Context.voiceReplacementsStore by preferencesDataStore("voice_replacements")

private object VoiceReplacementKeys {
    val json = stringPreferencesKey("rules_json")
}

class VoiceReplacementsStore(
    context: Context,
    private val scope: CoroutineScope,
) {
    private val app = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }

    var rules by mutableStateOf<List<VoiceReplacement>>(emptyList())
        private set

    init {
        scope.launch(Dispatchers.Main.immediate) {
            app.voiceReplacementsStore.data.map { prefs ->
                decode(prefs[VoiceReplacementKeys.json].orEmpty())
            }.flowOn(Dispatchers.IO).collect { rules = it }
        }
    }

    fun add(heard: String, write: String) {
        val h = heard.trim()
        val w = write.trim()
        if (h.isEmpty() || w.isEmpty()) return
        val next = rules + VoiceReplacement(UUID.randomUUID().toString(), h, w)
        persist(next)
    }

    fun update(id: String, heard: String, write: String) {
        val h = heard.trim()
        val w = write.trim()
        if (h.isEmpty() || w.isEmpty()) return
        val next = rules.map { if (it.id == id) it.copy(heard = h, write = w) else it }
        persist(next)
    }

    fun delete(id: String) {
        persist(rules.filter { it.id != id })
    }

    private fun persist(next: List<VoiceReplacement>) {
        rules = next
        scope.launch(Dispatchers.IO) {
            app.voiceReplacementsStore.edit { prefs ->
                prefs[VoiceReplacementKeys.json] = encode(next)
            }
        }
    }

    private fun encode(list: List<VoiceReplacement>): String {
        val rows = list.map { VoiceReplacementRow(it.id, it.heard, it.write) }
        return json.encodeToString(rows)
    }

    private fun decode(raw: String): List<VoiceReplacement> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            json.decodeFromString<List<VoiceReplacementRow>>(raw).map {
                VoiceReplacement(it.id, it.heard, it.write)
            }
        }.getOrDefault(emptyList())
    }
}
