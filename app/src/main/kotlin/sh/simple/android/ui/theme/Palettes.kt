package sh.simple.android.ui.theme

import androidx.compose.ui.graphics.Color

private fun hex(value: Long): Color = Color(0xFF000000 or value)

private data class RawPalette(
    val background: Color,
    val raised: Color,
    val card: Color,
    val mutedSurface: Color,
    val foreground: Color,
    val muted: Color,
    val primaryFill: Color,
    val onPrimary: Color,
    val focusRing: Color,
    val remove: Color,
    val attention: Color,
    val userBubble: Color,
    val userBubbleInk: Color,
    val brandSurface: Color,
    val brandAccent: Color,
    val codeBackground: Color,
)

private val darkAdd = hex(0x2FC58F)
private val lightAdd = hex(0x1A9E6A)

private fun assemble(raw: RawPalette, dark: Boolean): SimpleColors {
    val ink = if (dark) Color.White else Color.Black
    val faintAlpha = if (dark) 0.48f else 0.45f
    val glassShadowAlpha = if (dark) 0.32f else 0.08f
    return SimpleColors(
        background = raw.background,
        foreground = raw.foreground,
        muted = raw.muted,
        faint = raw.foreground.copy(alpha = faintAlpha),
        card = raw.card,
        raised = raw.raised,
        line = ink.copy(alpha = if (dark) 0.10f else 0.10f),
        hairline = ink.copy(alpha = if (dark) 0.075f else 0.06f),
        add = if (dark) darkAdd else lightAdd,
        remove = raw.remove,
        attention = raw.attention,
        glassFill = if (dark) ink.copy(alpha = 0.075f) else Color.White.copy(alpha = 0.72f),
        glassEdgeHighlight = if (dark) ink.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.90f),
        glassEdgeOutline = ink.copy(alpha = if (dark) 0.09f else 0.08f),
        glassShadow = raw.background.copy(alpha = glassShadowAlpha),
        sheetFill = raw.mutedSurface.copy(alpha = if (dark) 0.66f else 0.86f),
        menuFill = raw.card.copy(alpha = if (dark) 0.70f else 0.88f),
        dialogFill = raw.card.copy(alpha = if (dark) 0.70f else 0.88f),
        menuFillOpaque = raw.card,
        sheetFillOpaque = raw.mutedSurface,
        userBubble = raw.userBubble,
        userBubbleInk = raw.userBubbleInk,
        onPrimary = raw.onPrimary,
        primaryFill = raw.primaryFill,
        dialogScrim = raw.background.copy(alpha = 0.35f),
        sheetScrim = raw.background.copy(alpha = 0.48f),
        scrollMask = if (dark) raw.background else ink,
        codeBackground = raw.codeBackground,
        diffAddBackground = (if (dark) darkAdd else lightAdd).copy(alpha = 0.12f),
        diffRemoveBackground = raw.remove.copy(alpha = if (dark) 0.10f else 0.10f),
        sliderHandle = if (dark) ink else Color.White,
        focusRing = raw.focusRing,
        brandSurface = raw.brandSurface,
        brandAccent = raw.brandAccent,
        iconInk = ink,
    )
}

object AppPalettes {
    fun palette(theme: ThemeId, dark: Boolean): SimpleColors {
        val effectiveDark = dark || theme.darkOnly
        return assemble(sources(theme, effectiveDark), effectiveDark)
    }

    private fun sources(theme: ThemeId, dark: Boolean): RawPalette = when (theme) {
        ThemeId.Simple -> if (dark) {
            val fg = hex(0xF8F8F8)
            RawPalette(
                background = hex(0x000000),
                raised = hex(0x262626),
                card = hex(0x121212),
                mutedSurface = hex(0x2C2C2E),
                foreground = fg,
                muted = hex(0xA1A1A1),
                primaryFill = fg,
                onPrimary = hex(0x000000),
                focusRing = fg.copy(alpha = 0.45f),
                remove = hex(0xFF6467),
                attention = hex(0xE9C055),
                userBubble = hex(0x1C1C1E),
                userBubbleInk = fg,
                brandSurface = hex(0x000000),
                brandAccent = fg,
                codeBackground = hex(0x121212),
            )
        } else {
            val ink = hex(0x0A0A0A)
            RawPalette(
                background = Color.White,
                raised = hex(0xE8E8EA),
                card = hex(0xF4F4F5),
                mutedSurface = hex(0xF2F2F7),
                foreground = ink,
                muted = hex(0x6E6E73),
                primaryFill = ink,
                onPrimary = Color.White,
                focusRing = ink.copy(alpha = 0.45f),
                remove = hex(0xE5484D),
                attention = hex(0xB98900),
                userBubble = hex(0xEFEFF1),
                userBubbleInk = ink,
                brandSurface = hex(0xFBFAF6),
                brandAccent = hex(0x3B2FD0),
                codeBackground = hex(0xF6F6F7),
            )
        }
        ThemeId.Gruvbox -> if (dark) {
            val fg = hex(0xEBDBB2)
            RawPalette(
                background = hex(0x1D2021),
                raised = hex(0x282828),
                card = hex(0x32302F),
                mutedSurface = hex(0x3C3836),
                foreground = fg,
                muted = hex(0xA89984),
                primaryFill = fg,
                onPrimary = hex(0x1D2021),
                focusRing = hex(0x7C6F64),
                remove = hex(0xFB4934),
                attention = hex(0xFABD2F),
                userBubble = hex(0x3C3836),
                userBubbleInk = fg,
                brandSurface = hex(0x1D2021),
                brandAccent = hex(0x83A598),
                codeBackground = hex(0x32302F),
            )
        } else {
            val fg = hex(0x3C3836)
            RawPalette(
                background = hex(0xFBF1C7),
                raised = hex(0xF2E5BC),
                card = hex(0xF9F5D7),
                mutedSurface = hex(0xEBDBB2),
                foreground = fg,
                muted = hex(0x6E6258),
                primaryFill = fg,
                onPrimary = hex(0xFBF1C7),
                focusRing = hex(0x7C6F64),
                remove = hex(0x9D0006),
                attention = hex(0xB57614),
                userBubble = hex(0xEBDBB2),
                userBubbleInk = fg,
                brandSurface = hex(0xFBF1C7),
                brandAccent = hex(0x076678),
                codeBackground = hex(0xF9F5D7),
            )
        }
        ThemeId.Grey -> if (dark) {
            val fg = hex(0xF2F2F2)
            RawPalette(
                background = hex(0x0F0F0F),
                raised = hex(0x262626),
                card = hex(0x1A1A1A),
                mutedSurface = hex(0x2E2E2E),
                foreground = fg,
                muted = hex(0x9A9A9A),
                primaryFill = fg,
                onPrimary = hex(0x0F0F0F),
                focusRing = hex(0x737373),
                remove = hex(0xFF6467),
                attention = hex(0xE9C055),
                userBubble = hex(0x1F1F1F),
                userBubbleInk = fg,
                brandSurface = hex(0x0F0F0F),
                brandAccent = fg,
                codeBackground = hex(0x1A1A1A),
            )
        } else {
            val primary = hex(0x2393F1)
            val onPrimary = hex(0xFCFCFC)
            val fg = hex(0x171B1F)
            RawPalette(
                background = hex(0xEEF4FB),
                raised = hex(0xE6ECF2),
                card = hex(0xFAFCFE),
                mutedSurface = hex(0xE0E5EB),
                foreground = fg,
                muted = hex(0x65696F),
                primaryFill = hex(0x25292E),
                onPrimary = onPrimary,
                focusRing = hex(0x65696F),
                remove = hex(0xCC272E),
                attention = hex(0xBD8200),
                userBubble = primary,
                userBubbleInk = onPrimary,
                brandSurface = hex(0xEEF4FB),
                brandAccent = hex(0x0077C7),
                codeBackground = hex(0xFAFCFE),
            )
        }
        ThemeId.Catppuccin -> if (dark) {
            val fg = hex(0xCDD6F4)
            RawPalette(
                background = hex(0x181825),
                raised = hex(0x1E1E2E),
                card = hex(0x313244),
                mutedSurface = hex(0x45475A),
                foreground = fg,
                muted = hex(0xA6ADC8),
                primaryFill = fg,
                onPrimary = hex(0x181825),
                focusRing = hex(0x7F849C),
                remove = hex(0xF38BA8),
                attention = hex(0xF9E2AF),
                userBubble = hex(0x45475A),
                userBubbleInk = fg,
                brandSurface = hex(0x181825),
                brandAccent = hex(0x89B4FA),
                codeBackground = hex(0x313244),
            )
        } else {
            val fg = hex(0x45475A)
            RawPalette(
                background = hex(0xE6E9EF),
                raised = hex(0xDCE0E8),
                card = hex(0xEFF1F5),
                mutedSurface = hex(0xCCD0DA),
                foreground = fg,
                muted = hex(0x62657A),
                primaryFill = fg,
                onPrimary = hex(0xEFF1F5),
                focusRing = hex(0x8C8FA1),
                remove = hex(0xD20F39),
                attention = hex(0xDF8E1D),
                userBubble = hex(0xCCD0DA),
                userBubbleInk = fg,
                brandSurface = hex(0xE6E9EF),
                brandAccent = hex(0x1E66F5),
                codeBackground = hex(0xEFF1F5),
            )
        }
        ThemeId.OneDarkPro -> {
            val fg = hex(0xD7DAE0)
            RawPalette(
                background = hex(0x1E2227),
                raised = hex(0x23272E),
                card = hex(0x323842),
                mutedSurface = hex(0x3E4452),
                foreground = fg,
                muted = hex(0x9DA5B4),
                primaryFill = fg,
                onPrimary = hex(0x1E2227),
                focusRing = hex(0x4D78CC),
                remove = hex(0xE06C75),
                attention = hex(0xE5C07B),
                userBubble = hex(0x3E4452),
                userBubbleInk = fg,
                brandSurface = hex(0x1E2227),
                brandAccent = hex(0x61AFEF),
                codeBackground = hex(0x323842),
            )
        }
        ThemeId.Cobalt2 -> {
            val fg = Color.White
            RawPalette(
                background = hex(0x193549),
                raised = hex(0x243E51),
                card = hex(0x1F4662),
                mutedSurface = hex(0x355166),
                foreground = fg,
                muted = hex(0xAAAAAA),
                primaryFill = fg,
                onPrimary = hex(0x193549),
                focusRing = hex(0x437DA3),
                remove = hex(0xFF628C),
                attention = hex(0xFFC600),
                userBubble = hex(0x355166),
                userBubbleInk = fg,
                brandSurface = hex(0x193549),
                brandAccent = hex(0x9EFFFF),
                codeBackground = hex(0x1F4662),
            )
        }
    }
}
