package sh.simple.android.link

import sh.simple.android.ui.chat.parseInline
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import sh.simple.android.MainActivity
import sh.simple.android.R
import kotlinx.coroutines.launch
import sh.simple.android.link.RemoteGateway
import sh.simple.android.model.Item
import sh.simple.android.model.promptStyle

class InboxNotifications(
    private val context: Context,
) {
    private val manager = NotificationManagerCompat.from(context)

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val needs = NotificationChannel(CHANNEL_NEEDS, "Needs you", NotificationManager.IMPORTANCE_HIGH)
            val done = NotificationChannel(CHANNEL_DONE, "Done", NotificationManager.IMPORTANCE_DEFAULT)
            val connection = NotificationChannel(
                CHANNEL_CONNECTION,
                "Connection",
                NotificationManager.IMPORTANCE_MIN,
            ).apply {
                setShowBadge(false)
                setSound(null, null)
            }
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(needs)
            nm.createNotificationChannel(done)
            nm.createNotificationChannel(connection)
        }
    }

    @SuppressLint("MissingPermission")
    fun cancel(itemId: String) {
        manager.cancel(itemId.hashCode())
    }

    @SuppressLint("MissingPermission")
    fun onAdd(hostId: String, item: Item, agentTitle: String) {
        when {
            item.kind == "approval" -> postApproval(hostId, item, agentTitle)
            item.kind == "prompt" && item.promptStyle == "yesno" -> postYesNo(hostId, item, agentTitle)
            item.kind == "prompt" && item.promptStyle == "choice" -> postChoice(hostId, item, agentTitle)
            item.kind == "prompt" && item.promptStyle == "continue" -> postContinue(hostId, item, agentTitle)
            else -> postSimple(hostId, item, agentTitle)
        }
    }

    @SuppressLint("MissingPermission")
    private fun postApproval(hostId: String, item: Item, agentTitle: String) {
        val tap = pendingOpen(hostId, item.agent, item.id)
        val deny = pendingAnswer(hostId, item.id, "deny", null)
        val allow = pendingAnswer(hostId, item.id, "allow", null)
        val body = item.title.ifBlank { item.command.orEmpty() }
        val public = NotificationCompat.Builder(context, CHANNEL_NEEDS)
            .setSmallIcon(R.drawable.ic_stat_simple)
            .setContentTitle(agentTitle)
            .setContentText("Wants to run a command")
            .addAction(0, "Deny", deny)
            .addAction(0, "Allow", allow)
            .build()
        val notification = NotificationCompat.Builder(context, CHANNEL_NEEDS)
            .setSmallIcon(R.drawable.ic_stat_simple)
            .setContentTitle(agentTitle)
            .setContentText(body)
            .setContentIntent(tap)
            .addAction(0, "Deny", deny)
            .addAction(0, "Allow", allow)
            .setPublicVersion(public)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .build()
        manager.notify(item.id.hashCode(), notification)
    }

    @SuppressLint("MissingPermission")
    private fun postYesNo(hostId: String, item: Item, agentTitle: String) {
        val tap = pendingOpen(hostId, item.agent, item.id)
        val no = pendingAnswer(hostId, item.id, "deny", null)
        val yes = pendingAnswer(hostId, item.id, "allow", null)
        val body = item.title.ifBlank { item.body.orEmpty() }
        val notification = NotificationCompat.Builder(context, CHANNEL_NEEDS)
            .setSmallIcon(R.drawable.ic_stat_simple)
            .setContentTitle(agentTitle)
            .setContentText(body)
            .setContentIntent(tap)
            .addAction(0, "No", no)
            .addAction(0, "Yes", yes)
            .setAutoCancel(true)
            .build()
        manager.notify(item.id.hashCode(), notification)
    }

    @SuppressLint("MissingPermission")
    private fun postChoice(hostId: String, item: Item, agentTitle: String) {
        val tap = pendingOpen(hostId, item.agent, item.id)
        val body = item.title.ifBlank { item.body.orEmpty() }
        val builder = NotificationCompat.Builder(context, CHANNEL_NEEDS)
            .setSmallIcon(R.drawable.ic_stat_simple)
            .setContentTitle(agentTitle)
            .setContentText(body)
            .setContentIntent(tap)
            .setAutoCancel(true)
        for (opt in item.options.take(3)) {
            val label = opt.label.ifBlank { opt.id }
            builder.addAction(0, label, pendingAnswer(hostId, item.id, "allow", opt.id))
        }
        manager.notify(item.id.hashCode(), builder.build())
    }

    @SuppressLint("MissingPermission")
    private fun postContinue(hostId: String, item: Item, agentTitle: String) {
        val tap = pendingOpen(hostId, item.agent, item.id)
        val body = item.title.ifBlank { item.body.orEmpty() }
        val cont = pendingAnswer(hostId, item.id, "allow", null)
        val notification = NotificationCompat.Builder(context, CHANNEL_NEEDS)
            .setSmallIcon(R.drawable.ic_stat_simple)
            .setContentTitle(agentTitle)
            .setContentText(body)
            .setContentIntent(tap)
            .addAction(0, "Continue", cont)
            .setAutoCancel(true)
            .build()
        manager.notify(item.id.hashCode(), notification)
    }

    @SuppressLint("MissingPermission")
    private fun postSimple(hostId: String, item: Item, agentTitle: String) {
        val tap = pendingOpen(hostId, item.agent, item.id)
        val body = notificationBody(item)
        val notification = NotificationCompat.Builder(context, CHANNEL_NEEDS)
            .setSmallIcon(R.drawable.ic_stat_simple)
            .setContentTitle(agentTitle)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        manager.notify(item.id.hashCode(), notification)
    }

    private fun pendingOpen(hostId: String, agentId: String, itemId: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_HOST, hostId)
            putExtra(EXTRA_AGENT, agentId)
            putExtra(EXTRA_ITEM, itemId)
        }
        return PendingIntent.getActivity(
            context,
            itemId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun pendingAnswer(
        hostId: String,
        itemId: String,
        decision: String,
        option: String?,
    ): PendingIntent {
        val intent = Intent(context, InboxAnswerReceiver::class.java).apply {
            putExtra(EXTRA_HOST, hostId)
            putExtra(EXTRA_ITEM, itemId)
            putExtra(EXTRA_DECISION, decision)
            if (option != null) {
                putExtra(EXTRA_OPTION, option)
            }
        }
        val requestCode = (itemId + decision + (option ?: "")).hashCode()
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val CHANNEL_NEEDS = "needs_you"
        const val CHANNEL_DONE = "done"
        const val CHANNEL_CONNECTION = "connection"
        const val EXTRA_HOST = "host_id"
        const val EXTRA_AGENT = "agent_id"
        const val EXTRA_ITEM = "item_id"
        const val EXTRA_DECISION = "decision"
        const val EXTRA_OPTION = "option"
    }
}

class InboxAnswerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val hostId = intent.getStringExtra(InboxNotifications.EXTRA_HOST) ?: return
        val itemId = intent.getStringExtra(InboxNotifications.EXTRA_ITEM) ?: return
        val decision = intent.getStringExtra(InboxNotifications.EXTRA_DECISION) ?: return
        val option = intent.getStringExtra(InboxNotifications.EXTRA_OPTION)
        val app = context.applicationContext as sh.simple.android.App
        app.appScope.launch {
            val ok = rpcRun {
                RemoteGateway.inboxAnswer(app.hosts, hostId, itemId, decision, option, null)
            }.isSuccess
            if (ok) {
                app.inboxNotifications.cancel(itemId)
            }
        }
    }
}

fun notificationBody(item: Item): String {
    val reply = item.body?.trim().orEmpty()
    if (item.kind != "done" || reply.isEmpty()) return item.title.ifBlank { reply }
    val summary = reply.lineSequence()
        .map { it.trim().trimStart('#', '>', ' ') }
        .filter { it.isNotEmpty() && !it.startsWith("```") && !isTableRule(it) }
        .map { if (it.startsWith("|")) it.trim('|').split("|").joinToString(", ") { cell -> cell.trim() } else it }
        .joinToString(" ") { line -> parseInline(line).joinToString("") { it.text } }
    return if (summary.length > 240) summary.take(239).trimEnd() + "…" else summary
}

private fun isTableRule(line: String): Boolean = line.startsWith("|") && line.all { it in "|-: " }
