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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.terminalThemeStore: DataStore<Preferences> by preferencesDataStore("terminal_theme")

private object TerminalThemeKeys {
    val themeId = stringPreferencesKey("theme_id")
}

class TerminalThemeStore(context: Context, private val scope: CoroutineScope) {
    private val store = context.applicationContext.terminalThemeStore
    var themeId by mutableStateOf("simple_dark")
        private set

    init {
        scope.launch(Dispatchers.Main.immediate) {
            store.data.map { prefs ->
                prefs[TerminalThemeKeys.themeId] ?: "simple_dark"
            }.flowOn(Dispatchers.IO).collect { themeId = it }
        }
    }

    fun setTheme(id: String) {
        themeId = id
        scope.launch(Dispatchers.IO) {
            store.edit { prefs -> prefs[TerminalThemeKeys.themeId] = id }
        }
    }
}
