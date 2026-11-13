package sh.simple.android.ui.theme

enum class ThemeId(
    val stored: String,
    val label: String,
    val darkOnly: Boolean = false,
) {
    Simple("simple", "Simple"),
    Gruvbox("gruvbox", "Gruvbox"),
    Grey("grey", "Grey"),
    Catppuccin("catppuccin", "Catppuccin"),
    OneDarkPro("one_dark_pro", "One Dark Pro", darkOnly = true),
    Cobalt2("cobalt2", "Cobalt2", darkOnly = true),
    ;

    companion object {
        val pickerOrder = listOf(Simple, Gruvbox, Grey, Catppuccin, OneDarkPro, Cobalt2)

        fun fromStored(value: String?): ThemeId =
            entries.firstOrNull { it.stored == value } ?: Simple
    }
}
