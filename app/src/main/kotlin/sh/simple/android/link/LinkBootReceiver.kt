package sh.simple.android.link

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import sh.simple.android.App
import sh.simple.android.store.SimpleDatabase

class LinkBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> Unit
            else -> return
        }
        val app = context.applicationContext as? App ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Main.immediate).launch {
            try {
                if (!app.appSettings.readBackgroundAlertsBlocking()) return@launch
                if (pairedCount(app.db) < 1) return@launch
                context.startForegroundService(Intent(context, LinkForegroundService::class.java))
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun pairedCount(db: SimpleDatabase): Int =
        kotlinx.coroutines.withContext(Dispatchers.IO) { db.hosts().listAll().size }
}
