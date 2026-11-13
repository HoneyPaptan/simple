package sh.simple.android.link

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import sh.simple.android.MainActivity
import sh.simple.android.R
import sh.simple.android.App

class LinkForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watchJob: Job? = null
    private var lastBody = "Keeping host connections alive"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as App
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        postForeground(lastBody, openApp)
        if (watchJob?.isActive == true) return START_STICKY
        watchJob = scope.launch {
            app.hosts.start()
            updateBody(linkForegroundNotificationText(app.hosts.connectionSnapshot()), openApp)
            app.hosts.watchConnectionRows(this) { updated ->
                updateBody(linkForegroundNotificationText(updated), openApp)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun updateBody(body: String, openApp: PendingIntent) {
        if (body == lastBody) return
        lastBody = body
        postForeground(body, openApp)
    }

    private fun postForeground(body: String, openApp: PendingIntent) {
        val notification = NotificationCompat.Builder(this, InboxNotifications.CHANNEL_CONNECTION)
            .setSmallIcon(R.drawable.ic_stat_simple)
            .setContentTitle("simple")
            .setContentText(body)
            .setContentIntent(openApp)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val NOTIFICATION_ID = 42
    }
}
