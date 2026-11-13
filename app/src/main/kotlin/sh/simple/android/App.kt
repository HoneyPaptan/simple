package sh.simple.android

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import sh.simple.android.link.AndroidNetworkMonitor
import sh.simple.android.link.Hosts
import sh.simple.android.link.ForegroundChatAgent
import sh.simple.android.link.InboxNotifications
import sh.simple.android.link.LinkLifecycle
import sh.simple.android.store.AppSettingsStore
import sh.simple.android.store.CodeFontStore
import sh.simple.android.store.TerminalKeyBarStore
import sh.simple.android.store.TerminalThemeStore
import sh.simple.android.store.SimpleDatabase
import sh.simple.android.store.TokenVault
import sh.simple.android.ui.chat.ChatOutboundStore
import sh.simple.android.voice.VoiceHistoryStore
import sh.simple.android.voice.VoiceModelStore
import sh.simple.android.voice.VoiceReplacementsStore

class App : Application() {
    lateinit var db: SimpleDatabase
        private set
    lateinit var vault: TokenVault
        private set
    lateinit var hosts: Hosts
        private set
    lateinit var inboxNotifications: InboxNotifications
        private set
    lateinit var linkLifecycle: LinkLifecycle
        private set
    lateinit var appSettings: AppSettingsStore
        private set
    lateinit var voiceModels: VoiceModelStore
        private set
    lateinit var voiceReplacements: VoiceReplacementsStore
        private set
    lateinit var voiceHistory: VoiceHistoryStore
        private set
    lateinit var codeFont: CodeFontStore
        private set
    lateinit var terminalTheme: TerminalThemeStore
        private set
    lateinit var terminalKeys: TerminalKeyBarStore
        private set
    val chatOutbound = ChatOutboundStore()
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        db = SimpleDatabase.open(this)
        vault = TokenVault(this)
        appSettings = AppSettingsStore(this)
        voiceModels = VoiceModelStore(this, appScope)
        voiceReplacements = VoiceReplacementsStore(this, appScope)
        voiceHistory = VoiceHistoryStore(this, appScope)
        codeFont = CodeFontStore(this, appScope)
        terminalTheme = TerminalThemeStore(this, appScope)
        terminalKeys = TerminalKeyBarStore(this, appScope)
        inboxNotifications = InboxNotifications(this)
        linkLifecycle = LinkLifecycle(
            this,
            appScope,
            db,
            appSettings,
            startLinks = { hosts.start() },
            stopLinks = { hosts.stop() },
        )
        hosts = Hosts(db, vault, AndroidNetworkMonitor(this), appScope,
            onInboxAdd = { hostId, item ->
            appScope.launch {
                if (ForegroundChatAgent.isVisible(hostId, item.agent)) return@launch
                val agent = db.agents().listAll().firstOrNull { it.hostId == hostId && it.id == item.agent }
                val title = agent?.title ?: item.agent
                inboxNotifications.onAdd(hostId, item, title)
                linkLifecycle.onInboxActivity()
            }
        },
            onInboxUpdate = { _, item ->
                if (item.resolved) {
                    inboxNotifications.cancel(item.id)
                }
            },
        )
        linkLifecycle.attach()
    }
}
