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
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import sh.simple.android.model.WireJson

@Serializable
data class TerminalKeyEntry(
    val id: String,
    val label: String,
    val spec: String,
)

object TerminalKeyPresets {
    val catalog: List<TerminalKeyEntry> = listOf(
        TerminalKeyEntry("preset-ctrl-c", "Ctrl+C", "ctrl+c"),
        TerminalKeyEntry("preset-ctrl-d", "Ctrl+D", "ctrl+d"),
        TerminalKeyEntry("preset-ctrl-l", "Ctrl+L", "ctrl+l"),
        TerminalKeyEntry("preset-ctrl-r", "Ctrl+R", "ctrl+r"),
        TerminalKeyEntry("preset-ctrl-z", "Ctrl+Z", "ctrl+z"),
        TerminalKeyEntry("preset-alt-enter", "Alt+Enter", "alt+enter"),
        TerminalKeyEntry("preset-pipe", "|", "|"),
        TerminalKeyEntry("preset-tilde", "~", "~"),
        TerminalKeyEntry("preset-slash", "/", "/"),
    )
}

private val Context.terminalKeyBarStore: DataStore<Preferences> by preferencesDataStore("terminal_key_bar")

private object TerminalKeyBarKeys {
    val json = stringPreferencesKey("entries_json")
}

class TerminalKeyBarStore(context: Context, private val scope: CoroutineScope) {
    private val store = context.applicationContext.terminalKeyBarStore
    var entries by mutableStateOf<List<TerminalKeyEntry>>(emptyList())
        private set

    init {
        scope.launch(Dispatchers.Main.immediate) {
            store.data.map { prefs ->
                decode(prefs[TerminalKeyBarKeys.json])
            }.flowOn(Dispatchers.IO).collect { entries = it }
        }
    }

    fun add(entry: TerminalKeyEntry) {
        val next = entries + entry.copy(id = entry.id.ifEmpty { UUID.randomUUID().toString() })
        entries = next
        persist(next)
    }

    fun remove(id: String) {
        val next = entries.filterNot { it.id == id }
        entries = next
        persist(next)
    }

    fun moveUp(id: String) {
        val idx = entries.indexOfFirst { it.id == id }
        if (idx <= 0) return
        val next = entries.toMutableList()
        val item = next.removeAt(idx)
        next.add(idx - 1, item)
        entries = next
        persist(next)
    }

    fun moveDown(id: String) {
        val idx = entries.indexOfFirst { it.id == id }
        if (idx < 0 || idx >= entries.lastIndex) return
        val next = entries.toMutableList()
        val item = next.removeAt(idx)
        next.add(idx + 1, item)
        entries = next
        persist(next)
    }

    fun addSnippet(label: String, text: String) {
        val trimmed = text.take(64)
        if (trimmed.isEmpty()) return
        val name = label.trim().ifEmpty { trimmed.take(12) }
        add(TerminalKeyEntry(UUID.randomUUID().toString(), name, trimmed))
    }

    private fun persist(list: List<TerminalKeyEntry>) {
        scope.launch(Dispatchers.IO) {
            store.edit { prefs ->
                prefs[TerminalKeyBarKeys.json] = WireJson.encodeToString(ListSerializer(TerminalKeyEntry.serializer()), list)
            }
        }
    }

    private fun decode(raw: String?): List<TerminalKeyEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            WireJson.decodeFromString(ListSerializer(TerminalKeyEntry.serializer()), raw)
        }.getOrDefault(emptyList())
    }
}
