package sh.simple.android.ui.nav

sealed interface Screen {
    data object Welcome : Screen
    data class Pairing(val manualEntry: Boolean = false, val pendingUri: String? = null) : Screen
    data object Home : Screen
    data object Today : Screen
    data object Workspaces : Screen
    data object AllAgents : Screen
    data object NeedsYou : Screen
    data class Workspace(val hostId: String, val path: String) : Screen
    data class Chat(val hostId: String, val agentId: String) : Screen
    data class AgentScreen(val hostId: String, val agentId: String) : Screen
    data class Panes(val hostId: String, val workspace: String, val agentId: String? = null) : Screen
    data class Files(val hostId: String, val path: String) : Screen
    data class FileView(val hostId: String, val path: String) : Screen
    data class Changes(val hostId: String, val cwd: String) : Screen
    data class Diff(val hostId: String, val cwd: String, val path: String) : Screen
    data class Usage(val hostId: String) : Screen
    data class Servers(val hostId: String) : Screen
    data class Preview(val hostId: String, val port: Int, val previewPath: String, val session: String) : Screen
    data class Webhooks(val hostId: String) : Screen
    data object Settings : Screen
    data object Voice : Screen
    data object CodeFont : Screen
    data object TerminalTheme : Screen
    data object TerminalKeys : Screen
    data object KitGallery : Screen
}
