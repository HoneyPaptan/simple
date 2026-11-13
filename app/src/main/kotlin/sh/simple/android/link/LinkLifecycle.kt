package sh.simple.android.link

import android.content.Context
import android.content.Intent
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.simple.android.model.Item
import sh.simple.android.model.WireJson
import sh.simple.android.store.AppSettingsStore
import sh.simple.android.store.SimpleDatabase
import sh.simple.android.ui.inbox.needsYouKinds

class LinkLifecycle(
    private val context: Context,
    private val scope: CoroutineScope,
    private val db: SimpleDatabase,
    private val appSettings: AppSettingsStore,
    private val startLinks: suspend () -> Unit,
    private val stopLinks: suspend () -> Unit,
) {
    private var stopJob: Job? = null
    @Volatile private var foreground = false

    fun attach() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    foreground = true
                    stopJob?.cancel()
                    context.stopService(Intent(context, LinkForegroundService::class.java))
                    scope.launch { startLinks() }
                }

                override fun onStop(owner: LifecycleOwner) {
                    foreground = false
                    scope.launch { onBackground() }
                }
            },
        )
    }

    fun onInboxActivity() {
        if (foreground) return
        scope.launch { onBackground() }
    }

    fun onAppSettingsChanged() {
        if (foreground) return
        scope.launch { onBackground() }
    }

    private suspend fun onBackground() {
        stopJob?.cancel()
        val backgroundAlerts = appSettings.settings.backgroundAlerts
        val paired = pairedHostCount()
        val openItems = openItemCount()
        if (shouldRunLinkForegroundService(backgroundAlerts, paired, openItems, foreground)) {
            context.startForegroundService(Intent(context, LinkForegroundService::class.java))
        }
        when {
            backgroundAlerts && paired > 0 -> Unit
            openItems > 0 -> scheduleStop(IDLE_STOP_MS)
            else -> scheduleStop(GRACE_STOP_MS)
        }
    }

    private fun scheduleStop(afterMs: Long) {
        stopJob = scope.launch {
            delay(afterMs)
            if (foreground) return@launch
            val backgroundAlerts = appSettings.settings.backgroundAlerts
            val paired = pairedHostCount()
            val openItems = openItemCount()
            if (afterMs == IDLE_STOP_MS && openItems > 0) return@launch
            if (backgroundAlerts && paired > 0) return@launch
            context.stopService(Intent(context, LinkForegroundService::class.java))
            stopLinks()
        }
    }

    private suspend fun pairedHostCount(): Int = withContext(Dispatchers.IO) {
        db.hosts().listAll().size
    }

    private suspend fun openItemCount(): Int = withContext(Dispatchers.IO) {
        db.items().listAll().count { row ->
            val item = runCatching { WireJson.decodeFromString<Item>(row.json) }.getOrNull() ?: return@count false
            !item.resolved && item.kind in needsYouKinds
        }
    }

    private companion object {
        const val GRACE_STOP_MS = 60_000L
        const val IDLE_STOP_MS = 30 * 60 * 1000L
    }
}
