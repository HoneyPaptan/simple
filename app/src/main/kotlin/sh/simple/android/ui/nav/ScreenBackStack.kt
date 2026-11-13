package sh.simple.android.ui.nav

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.snapshots.SnapshotStateList

private const val SEP = '\u001f'

fun encodeScreen(screen: Screen): String? = when (screen) {
    Screen.Welcome -> "welcome"
    is Screen.Pairing -> if (screen.pendingUri != null) null else "pairing${SEP}${screen.manualEntry}"
    Screen.Today -> "home"
    Screen.Home -> "home"
    Screen.Workspaces -> "workspaces"
    Screen.AllAgents -> "all_agents"
    Screen.NeedsYou -> "needs_you"
    is Screen.Workspace -> "workspace${SEP}${screen.hostId}${SEP}${screen.path}"
    is Screen.Chat -> "chat${SEP}${screen.hostId}${SEP}${screen.agentId}"
    is Screen.AgentScreen -> "terminal${SEP}${screen.hostId}${SEP}${screen.agentId}"
    is Screen.Panes -> "panes${SEP}${screen.hostId}${SEP}${screen.workspace}${SEP}${screen.agentId.orEmpty()}"
    is Screen.Files -> "files${SEP}${screen.hostId}${SEP}${screen.path}"
    is Screen.FileView -> "file_view${SEP}${screen.hostId}${SEP}${screen.path}"
    is Screen.Changes -> "changes${SEP}${screen.hostId}${SEP}${screen.cwd}"
    is Screen.Diff -> "diff${SEP}${screen.hostId}${SEP}${screen.cwd}${SEP}${screen.path}"
    is Screen.Usage -> "usage${SEP}${screen.hostId}"
    is Screen.Servers -> "servers${SEP}${screen.hostId}"
    is Screen.Preview -> "preview${SEP}${screen.hostId}${SEP}${screen.port}${SEP}${screen.previewPath}${SEP}${screen.session}"
    is Screen.Webhooks -> "webhooks${SEP}${screen.hostId}"
    Screen.Settings -> "settings"
    Screen.Voice -> "voice"
    Screen.CodeFont -> "code_font"
    Screen.TerminalTheme -> "terminal_theme"
    Screen.TerminalKeys -> "terminal_keys"
    Screen.KitGallery -> "kit_gallery"
}

fun decodeScreen(encoded: String): Screen? {
  val idx = encoded.indexOf(SEP)
  val head = if (idx < 0) encoded else encoded.substring(0, idx)
  val tail = if (idx < 0) "" else encoded.substring(idx + 1)
  fun fields(): List<String> = if (tail.isEmpty()) emptyList() else tail.split(SEP)
  return when (head) {
    "welcome" -> Screen.Welcome
    "pairing" -> {
      val manual = fields().firstOrNull()?.toBooleanStrictOrNull() ?: false
      Screen.Pairing(manualEntry = manual)
    }
    "home" -> Screen.Today
    "workspaces" -> Screen.Workspaces
    "all_agents" -> Screen.AllAgents
    "needs_you" -> Screen.NeedsYou
    "workspace" -> {
      val f = fields()
      if (f.size < 2) return null
      Screen.Workspace(f[0], f[1])
    }
    "chat" -> {
      val f = fields()
      if (f.size < 2) return null
      Screen.Chat(f[0], f[1])
    }
    "terminal" -> {
      val f = fields()
      if (f.size < 2) return null
      Screen.AgentScreen(f[0], f[1])
    }
    "panes" -> {
      val f = fields()
      if (f.size < 2) return null
      val agent = f.getOrNull(2)?.takeIf { it.isNotEmpty() }
      Screen.Panes(f[0], f[1], agent)
    }
    "files" -> {
      val f = fields()
      if (f.size < 2) return null
      Screen.Files(f[0], f[1])
    }
    "file_view" -> {
      val f = fields()
      if (f.size < 2) return null
      Screen.FileView(f[0], f[1])
    }
    "changes" -> {
      val f = fields()
      if (f.size < 2) return null
      Screen.Changes(f[0], f[1])
    }
    "diff" -> {
      val f = fields()
      if (f.size < 3) return null
      Screen.Diff(f[0], f[1], f[2])
    }
    "usage" -> {
      val f = fields()
      if (f.isEmpty()) return null
      Screen.Usage(f[0])
    }
    "servers" -> {
      val f = fields()
      if (f.isEmpty()) return null
      Screen.Servers(f[0])
    }
    "preview" -> {
      val f = fields()
      if (f.size < 4) return null
      val port = f[1].toIntOrNull() ?: return null
      Screen.Preview(f[0], port, f[2], f[3])
    }
    "webhooks" -> {
      val f = fields()
      if (f.isEmpty()) return null
      Screen.Webhooks(f[0])
    }
    "settings" -> Screen.Settings
    "voice" -> Screen.Voice
    "code_font" -> Screen.CodeFont
    "terminal_theme" -> Screen.TerminalTheme
    "terminal_keys" -> Screen.TerminalKeys
    "kit_gallery" -> Screen.KitGallery
    else -> null
  }
}

fun encodeBackStack(stack: List<Screen>): String =
    stack.mapNotNull { encodeScreen(it) }.joinToString("\n")

fun decodeBackStack(encoded: String): List<Screen> =
    encoded.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .mapNotNull { decodeScreen(it) }
        .toList()

val screenBackStackSaver: Saver<SnapshotStateList<Screen>, String> = Saver(
    save = { encodeBackStack(it) },
    restore = { saved ->
        mutableStateListOf<Screen>().also { it.addAll(decodeBackStack(saved)) }
    },
)
