package sh.simple.android.ui

import androidx.compose.foundation.ScrollState
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import sh.simple.android.App
import sh.simple.android.link.ForegroundChatAgent
import sh.simple.android.ui.agent.AgentScreen
import sh.simple.android.ui.pane.PanesScreen
import sh.simple.android.ui.agents.AllAgentsScreen
import sh.simple.android.ui.agents.WorkspaceAgentsScreen
import sh.simple.android.ui.changes.ChangesScreen
import sh.simple.android.ui.changes.DiffScreen
import sh.simple.android.ui.chat.ChatScreen
import sh.simple.android.ui.files.FileViewerScreen
import sh.simple.android.ui.files.FilesScreen
import sh.simple.android.ui.home.TodayScreen
import sh.simple.android.ui.home.WorkspacesScreen
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import sh.simple.android.ui.glass.GlassBudgetOverlay
import sh.simple.android.ui.glass.LocalGlassSurfaceCounter
import sh.simple.android.ui.glass.rememberGlassSurfaceCounter
import sh.simple.android.ui.kit.OverlayRoot
import sh.simple.android.ui.nav.Screen
import sh.simple.android.ui.nav.screenBackStackSaver
import sh.simple.android.ui.nav.shouldReopenSettingsAfterPop
import sh.simple.android.ui.pair.PairScreen
import sh.simple.android.ui.needsyou.NeedsYouScreen
import sh.simple.android.ui.settings.CodeFontScreen
import sh.simple.android.ui.settings.TerminalKeysScreen
import sh.simple.android.ui.settings.TerminalThemeScreen
import sh.simple.android.ui.settings.SettingsSheet
import sh.simple.android.ui.settings.VoiceSettingsScreen
import sh.simple.android.ui.settings.WebhooksScreen
import sh.simple.android.ui.theme.SimpleTheme
import sh.simple.android.ui.theme.rememberAppearanceController
import sh.simple.android.ui.theme.rememberCodeFontFamily
import sh.simple.android.ui.usage.UsageScreen
import sh.simple.android.ui.servers.PreviewScreen
import sh.simple.android.ui.servers.ServersScreen
import sh.simple.android.ui.security.AppLockGate
import sh.simple.android.ui.security.rememberAppLockState
import sh.simple.android.ui.welcome.WelcomeScreen
import sh.simple.android.store.rememberHostsForDisplay

@Composable
fun SimpleApp(app: App, pendingPairUri: String? = null, modifier: Modifier = Modifier) {
    val appearance = rememberAppearanceController()
    val codeFontFamily = rememberCodeFontFamily(app.codeFont)
    val backStack = rememberSaveable(saver = screenBackStackSaver) { mutableStateListOf<Screen>() }
    val loadedHosts by app.db.rememberHostsForDisplay().collectAsState(initial = null)
    val hosts = loadedHosts.orEmpty()
    var settingsOpen by remember { mutableStateOf(false) }
    var settingsScroll by remember { mutableStateOf(ScrollState(0)) }
    var openComputers by remember { mutableStateOf(false) }
    var reopenSettingsAfterComputers by remember { mutableStateOf(false) }
    var reopenSettingsAfterUsage by remember { mutableStateOf(false) }
    LaunchedEffect(loadedHosts, pendingPairUri) {
        if (backStack.isNotEmpty()) return@LaunchedEffect
        if (loadedHosts == null && pendingPairUri == null) return@LaunchedEffect
        when {
            pendingPairUri != null -> backStack.add(Screen.Pairing(manualEntry = true, pendingUri = pendingPairUri))
            hosts.isEmpty() -> backStack.add(Screen.Welcome)
            else -> backStack.add(Screen.Today)
        }
    }
    LaunchedEffect(pendingPairUri) {
        val uri = pendingPairUri ?: return@LaunchedEffect
        val top = backStack.lastOrNull()
        if (top is Screen.Pairing && top.pendingUri == uri) return@LaunchedEffect
        backStack.clear()
        backStack.add(Screen.Pairing(manualEntry = true, pendingUri = uri))
    }
    val hostNames = hosts.associate { it.id to it.name }
    fun navigate(screen: Screen) {
        backStack.add(screen)
    }
    fun back() {
        if (backStack.size <= 1) return
        val top = backStack.removeAt(backStack.lastIndex)
        if (shouldReopenSettingsAfterPop(top, reopenSettingsAfterUsage)) {
            if (top is Screen.Usage) reopenSettingsAfterUsage = false
            settingsOpen = true
        }
    }
    fun swapAgent(newHostId: String, newAgentId: String) {
        val index = backStack.lastIndex
        if (index < 0) return
        when (val top = backStack[index]) {
            is Screen.Chat -> backStack[index] = Screen.Chat(newHostId, newAgentId)
            is Screen.AgentScreen -> backStack[index] = Screen.AgentScreen(newHostId, newAgentId)
            else -> Unit
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val topScreen = backStack.lastOrNull()
    LaunchedEffect(topScreen) {
        when (val screen = topScreen) {
            is Screen.Chat -> ForegroundChatAgent.set(screen.hostId, screen.agentId)
            else -> ForegroundChatAgent.clear()
        }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> ForegroundChatAgent.clear()
                Lifecycle.Event.ON_START -> {
                    val screen = backStack.lastOrNull()
                    if (screen is Screen.Chat) ForegroundChatAgent.set(screen.hostId, screen.agentId)
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    if (!appearance.loaded) return
    val systemDark = isSystemInDarkTheme()
    val colors = appearance.settings.resolveColors(systemDark)
    val view = LocalView.current
    SideEffect {
        val window = (view.context as android.app.Activity).window
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(colors.background.toArgb()))
        val controller = WindowCompat.getInsetsController(window, view)
        val lightBars = appearance.settings.usesLightSystemBars(systemDark)
        controller.isAppearanceLightStatusBars = lightBars
        controller.isAppearanceLightNavigationBars = lightBars
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
    }
    val glassCounter = rememberGlassSurfaceCounter()
    val appLock = rememberAppLockState(app.appSettings)
    LaunchedEffect(app.appSettings.loaded, app.appSettings.settings.appLock) {
        if (app.appSettings.loaded) appLock.onSettingChanged(app.appSettings.settings.appLock)
    }
    SimpleTheme(appearance.settings, colors, codeFontFamily) {
        if (appLock.locked) {
            AppLockGate(locked = true, onUnlocked = { appLock.unlock() }, modifier = Modifier.fillMaxSize())
            return@SimpleTheme
        }
        CompositionLocalProvider(LocalGlassSurfaceCounter provides glassCounter) {
        OverlayRoot {
            Box(Modifier.fillMaxSize()) {
            BackHandler(backStack.size > 1) { back() }
            when (val screen = backStack.lastOrNull()) {
                null -> Unit
                Screen.Welcome -> WelcomeScreen(
                    onScan = { navigate(Screen.Pairing()) },
                    onEnterCode = { navigate(Screen.Pairing(manualEntry = true)) },
                )
                is Screen.Pairing -> PairScreen(
                    hosts = app.hosts,
                    db = app.db,
                    vault = app.vault,
                    deviceName = android.os.Build.MODEL,
                    deviceModel = android.os.Build.MODEL,
                    manualEntry = screen.manualEntry,
                    initialUri = screen.pendingUri,
                    onDone = {
                        backStack.clear()
                        backStack.add(Screen.Today)
                    },
                    onBack = { back() },
                )
                Screen.Home, Screen.Today -> TodayScreen(
                    db = app.db,
                    hosts = app.hosts,
                    onNavigate = { navigate(it) },
                    onOpenSettings = { settingsOpen = true },
                    openComputers = openComputers,
                    onComputersOpened = { openComputers = false },
                    onComputersDismiss = {
                        if (reopenSettingsAfterComputers) {
                            reopenSettingsAfterComputers = false
                            settingsOpen = true
                        }
                    },
                    voiceStore = app.voiceModels,
                    voiceReplacements = app.voiceReplacements,
                    voiceHistory = app.voiceHistory,
                )
                Screen.Workspaces -> WorkspacesScreen(
                    db = app.db,
                    hosts = app.hosts,
                    onNavigate = { navigate(it) },
                    onOpenSettings = { settingsOpen = true },
                    openComputers = openComputers,
                    onComputersOpened = { openComputers = false },
                    onComputersDismiss = {
                        if (reopenSettingsAfterComputers) {
                            reopenSettingsAfterComputers = false
                            settingsOpen = true
                        }
                    },
                    voiceStore = app.voiceModels,
                    voiceReplacements = app.voiceReplacements,
                    voiceHistory = app.voiceHistory,
                )
                Screen.AllAgents -> AllAgentsScreen(
                    db = app.db,
                    hosts = app.hosts,
                    hostNames = hostNames,
                    onBack = { back() },
                    onOpenChat = { h, a -> navigate(Screen.Chat(h, a)) },
                    onNavigate = { navigate(it) },
                )
                Screen.NeedsYou -> NeedsYouScreen(
                    db = app.db,
                    hosts = app.hosts,
                    hostNames = hostNames,
                    onBack = { back() },
                )
                is Screen.Workspace -> WorkspaceAgentsScreen(
                    db = app.db,
                    hosts = app.hosts,
                    hostId = screen.hostId,
                    path = screen.path,
                    hostNames = hostNames,
                    onBack = { back() },
                    onOpenChat = { h, a -> navigate(Screen.Chat(h, a)) },
                    onNavigate = { navigate(it) },
                )
                is Screen.Chat -> ChatScreen(
                    db = app.db,
                    hosts = app.hosts,
                    hostId = screen.hostId,
                    agentId = screen.agentId,
                    hostNames = hostNames,
                    onBack = { back() },
                    onOpenAgentScreen = { navigate(Screen.AgentScreen(screen.hostId, screen.agentId)) },
                    onOpenChat = { h, a -> navigate(Screen.Chat(h, a)) },
                    onSwapAgent = { h, a -> swapAgent(h, a) },
                    onNavigate = { navigate(it) },
                    voiceStore = app.voiceModels,
                    voiceReplacements = app.voiceReplacements,
                    voiceHistory = app.voiceHistory,
                )
                is Screen.Files -> FilesScreen(
                    hosts = app.hosts,
                    hostId = screen.hostId,
                    startPath = screen.path,
                    onBack = { back() },
                    onOpenFile = { navigate(Screen.FileView(screen.hostId, it)) },
                )
                is Screen.FileView -> FileViewerScreen(
                    hosts = app.hosts,
                    hostId = screen.hostId,
                    path = screen.path,
                    onBack = { back() },
                )
                is Screen.Changes -> ChangesScreen(
                    hosts = app.hosts,
                    hostId = screen.hostId,
                    cwd = screen.cwd,
                    onBack = { back() },
                    onOpenDiff = { navigate(Screen.Diff(screen.hostId, screen.cwd, it)) },
                )
                is Screen.Diff -> DiffScreen(
                    hosts = app.hosts,
                    hostId = screen.hostId,
                    cwd = screen.cwd,
                    path = screen.path,
                    onBack = { back() },
                )
                is Screen.Usage -> UsageScreen(
                    hosts = app.hosts,
                    hostId = screen.hostId,
                    hostName = hostNames[screen.hostId] ?: screen.hostId,
                    onBack = { back() },
                )
                is Screen.Servers -> ServersScreen(
                    hosts = app.hosts,
                    hostId = screen.hostId,
                    title = hostNames[screen.hostId] ?: screen.hostId,
                    onBack = { back() },
                    onNavigate = { navigate(it) },
                )
                is Screen.Preview -> PreviewScreen(
                    db = app.db,
                    hostId = screen.hostId,
                    port = screen.port,
                    previewPath = screen.previewPath,
                    session = screen.session,
                    onBack = { back() },
                )
                is Screen.Webhooks -> WebhooksScreen(
                    hosts = app.hosts,
                    hostId = screen.hostId,
                    hostName = hostNames[screen.hostId] ?: screen.hostId,
                    onBack = { back() },
                )
                is Screen.Panes -> PanesScreen(
                    hosts = app.hosts,
                    db = app.db,
                    hostId = screen.hostId,
                    workspace = screen.workspace,
                    highlightAgentId = screen.agentId,
                    onBack = { back() },
                    onOpenChat = { id -> navigate(Screen.Chat(screen.hostId, id)) },
                    onOpenTerminal = { id -> navigate(Screen.AgentScreen(screen.hostId, id)) },
                )
                is Screen.AgentScreen -> AgentScreen(
                    hosts = app.hosts,
                    db = app.db,
                    hostId = screen.hostId,
                    agentId = screen.agentId,
                    title = "Terminal",
                    terminalTheme = app.terminalTheme,
                    terminalKeys = app.terminalKeys,
                    codeFont = app.codeFont,
                    onBack = { back() },
                    onSwapAgent = { h, a -> swapAgent(h, a) },
                )
                Screen.Settings -> WorkspacesScreen(
                    db = app.db,
                    hosts = app.hosts,
                    onNavigate = { navigate(it) },
                    onOpenSettings = { settingsOpen = true },
                    openComputers = openComputers,
                    onComputersOpened = { openComputers = false },
                    onComputersDismiss = {
                        if (reopenSettingsAfterComputers) {
                            reopenSettingsAfterComputers = false
                            settingsOpen = true
                        }
                    },
                    voiceStore = app.voiceModels,
                    voiceReplacements = app.voiceReplacements,
                    voiceHistory = app.voiceHistory,
                )
                Screen.KitGallery -> KitGallery(onExit = { back() })
                Screen.Voice -> VoiceSettingsScreen(
                    store = app.voiceModels,
                    replacements = app.voiceReplacements,
                    history = app.voiceHistory,
                    onBack = { back() },
                )
                Screen.CodeFont -> CodeFontScreen(
                    db = app.db,
                    hosts = app.hosts,
                    vault = app.vault,
                    store = app.codeFont,
                    hostRows = hosts,
                    onBack = { back() },
                )
                Screen.TerminalTheme -> TerminalThemeScreen(
                    store = app.terminalTheme,
                    onBack = { back() },
                )
                Screen.TerminalKeys -> TerminalKeysScreen(
                    store = app.terminalKeys,
                    onBack = { back() },
                )
            }
            SettingsSheet(
                settingsOpen,
                {
                    settingsOpen = false
                    settingsScroll = ScrollState(0)
                },
                app.db,
                app.hosts,
                app.appSettings,
                app.linkLifecycle,
                appearance,
                app.codeFont,
                app.terminalTheme,
                app.terminalKeys,
                onOpenKitGallery = { settingsOpen = false; navigate(Screen.KitGallery) },
                onOpenComputers = {
                    if (settingsOpen) reopenSettingsAfterComputers = true
                    settingsOpen = false
                    openComputers = true
                },
                onOpenCodeFont = {
                    settingsOpen = false
                    navigate(Screen.CodeFont)
                },
                onOpenTerminalTheme = {
                    settingsOpen = false
                    navigate(Screen.TerminalTheme)
                },
                onOpenTerminalKeys = {
                    settingsOpen = false
                    navigate(Screen.TerminalKeys)
                },
                onOpenUsage = { hostId ->
                    settingsOpen = false
                    reopenSettingsAfterUsage = true
                    navigate(Screen.Usage(hostId))
                },
                onOpenWebhooks = { hostId ->
                    settingsOpen = false
                    navigate(Screen.Webhooks(hostId))
                },
                onOpenVoice = {
                    settingsOpen = false
                    navigate(Screen.Voice)
                },
                scrollState = settingsScroll,
            )
            GlassBudgetOverlay(Modifier.align(Alignment.TopEnd))
            }
        }
        }
    }
}

