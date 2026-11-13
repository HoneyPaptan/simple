package sh.simple.android.ui.terminal

data class TerminalPalette(
    val id: String,
    val label: String,
    val source: String,
    val foreground: String,
    val background: String,
    val cursor: String,
    val ansi: List<String>,
)

object TerminalThemes {
  val simpleDark = TerminalPalette(
        id = "simple_dark",
        label = "Simple dark",
        source = "simple",
        foreground = "#F8F8F8",
        background = "#000000",
        cursor = "#F8F8F8",
        ansi = listOf(
            "#000000", "#CD3131", "#0DBC79", "#E5E510", "#2472C8", "#BC3FBC", "#11A8CD", "#E5E5E5",
            "#666666", "#F14C4C", "#23D18B", "#F5F543", "#3B8EEA", "#D670D6", "#29B8DB", "#FFFFFF",
        ),
    )

    val simpleLight = TerminalPalette(
        id = "simple_light",
        label = "Simple light",
        source = "simple",
        foreground = "#0A0A0A",
        background = "#FFFFFF",
        cursor = "#0A0A0A",
        ansi = listOf(
            "#000000", "#C91B00", "#008B45", "#AB6B00", "#005BC6", "#A100BF", "#008B8B", "#6E6E73",
            "#8E8E93", "#FF3B30", "#34C759", "#FF9500", "#007AFF", "#AF52DE", "#32ADE6", "#1C1C1E",
        ),
    )

    val solarizedDark = TerminalPalette(
        id = "solarized_dark",
        label = "Solarized Dark",
        source = "Ethan Schoonover (MIT)",
        foreground = "#839496",
        background = "#002B36",
        cursor = "#93A1A1",
        ansi = listOf(
            "#073642", "#DC322F", "#859900", "#B58900", "#268BD2", "#D33682", "#2AA198", "#EEE8D5",
            "#002B36", "#CB4B16", "#586E75", "#657B83", "#839496", "#6C71C4", "#93A1A1", "#FDF6E3",
        ),
    )

    val nord = TerminalPalette(
        id = "nord",
        label = "Nord",
        source = "Arctic Ice Studio (MIT)",
        foreground = "#D8DEE9",
        background = "#2E3440",
        cursor = "#D8DEE9",
        ansi = listOf(
            "#3B4252", "#BF616A", "#A3BE8C", "#EBCB8B", "#81A1C1", "#B48EAD", "#88C0D0", "#E5E9F0",
            "#4C566A", "#BF616A", "#A3BE8C", "#EBCB8B", "#81A1C1", "#B48EAD", "#8FBCBB", "#ECEFF4",
        ),
    )

    val gruvboxDark = TerminalPalette(
        id = "gruvbox_dark",
        label = "Gruvbox dark",
        source = "Pavel Pertsev (MIT)",
        foreground = "#EBDBB2",
        background = "#282828",
        cursor = "#EBDBB2",
        ansi = listOf(
            "#282828", "#CC241D", "#98971A", "#D79921", "#458588", "#B16286", "#689D6A", "#A89984",
            "#928374", "#FB4934", "#B8BB26", "#FABD2F", "#83A598", "#D3869B", "#8EC07C", "#EBDBB2",
        ),
    )

    val dracula = TerminalPalette(
        id = "dracula",
        label = "Dracula",
        source = "Dracula Theme (MIT)",
        foreground = "#F8F8F2",
        background = "#282A36",
        cursor = "#F8F8F2",
        ansi = listOf(
            "#21222C", "#FF5555", "#50FA7B", "#F1FA8C", "#BD93F9", "#FF79C6", "#8BE9FD", "#F8F8F2",
            "#6272A4", "#FF6E6E", "#69FF94", "#FFFFA5", "#D6ACFF", "#FF92DF", "#A4FFFF", "#FFFFFF",
        ),
    )

    val catalog: List<TerminalPalette> = listOf(
        simpleDark,
        simpleLight,
        solarizedDark,
        nord,
        gruvboxDark,
        dracula,
    )

    private val byId = catalog.associateBy { it.id }

    fun resolve(themeId: String, appDark: Boolean): TerminalPalette = when (themeId) {
        "match" -> if (appDark) simpleDark else simpleLight
        else -> byId[themeId] ?: simpleDark
    }

    fun labelFor(id: String): String = when (id) {
        "match" -> "Match app"
        else -> byId[id]?.label ?: simpleDark.label
    }
}
