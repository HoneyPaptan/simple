package sh.simple.android.store

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

data class AppSettings(
    val backgroundAlerts: Boolean = true,
    val appLock: Boolean = false,
)

private val Context.appSettingsStore: DataStore<Preferences> by preferencesDataStore("app_settings")

private object AppSettingsKeys {
    val backgroundAlerts = booleanPreferencesKey("background_alerts")
    val appLock = booleanPreferencesKey("app_lock")
}

class AppSettingsStore(context: Context) {
    private val store = context.applicationContext.appSettingsStore
    var settings by mutableStateOf(AppSettings())
        private set
    var loaded by mutableStateOf(false)
        private set

    init {
        CoroutineScope(Dispatchers.Main.immediate).launch {
            store.data.map { prefs ->
                AppSettings(
                    backgroundAlerts = prefs[AppSettingsKeys.backgroundAlerts] ?: true,
                    appLock = prefs[AppSettingsKeys.appLock] ?: false,
                )
            }.flowOn(Dispatchers.IO).collect {
                settings = it
                loaded = true
            }
        }
    }

    fun setBackgroundAlerts(on: Boolean) {
        val next = settings.copy(backgroundAlerts = on)
        settings = next
        CoroutineScope(Dispatchers.IO).launch {
            store.edit { prefs ->
                prefs[AppSettingsKeys.backgroundAlerts] = on
            }
        }
    }

    fun readBackgroundAlertsBlocking(): Boolean = runBlocking(Dispatchers.IO) {
        store.data.map { prefs -> prefs[AppSettingsKeys.backgroundAlerts] ?: true }.first()
    }

    fun readAppLockBlocking(): Boolean = runBlocking(Dispatchers.IO) {
        store.data.map { prefs -> prefs[AppSettingsKeys.appLock] ?: false }.first()
    }

    fun setAppLock(on: Boolean) {
        val next = settings.copy(appLock = on)
        settings = next
        CoroutineScope(Dispatchers.IO).launch {
            store.edit { prefs ->
                prefs[AppSettingsKeys.appLock] = on
            }
        }
    }
}
