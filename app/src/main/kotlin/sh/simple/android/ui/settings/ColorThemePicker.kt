package sh.simple.android.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import sh.simple.android.ui.kit.AppText
import sh.simple.android.ui.kit.SectionLabel
import sh.simple.android.ui.kit.Segmented
import sh.simple.android.ui.theme.AppPalettes
import sh.simple.android.ui.theme.AppearanceController
import sh.simple.android.ui.theme.AppearanceSettings
import sh.simple.android.ui.theme.ColorSchemePreference
import sh.simple.android.ui.theme.SimpleColors
import sh.simple.android.ui.theme.ThemeId
import sh.simple.android.ui.theme.Tokens
import sh.simple.android.ui.theme.Type

private val SwatchWidth = 84.dp
private val SwatchShape = RoundedCornerShape(12.dp)
private val SettingsRowPadding = Modifier.padding(start = 51.dp, end = 20.dp)

@Composable
fun ColorThemeSection(settings: AppearanceSettings, appearance: AppearanceController) {
    val systemDark = isSystemInDarkTheme()
    val previewDark = settings.resolvesDark(systemDark)
    SectionLabel("Color theme", Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp))
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
    ) {
        items(ThemeId.pickerOrder, key = { it.stored }) { theme ->
            val swatchDark = theme.darkOnly || previewDark
            ThemeSwatchCard(
                label = theme.label,
                colors = AppPalettes.palette(theme, swatchDark),
                selected = settings.themeId == theme,
                onClick = { appearance.update { it.copy(themeId = theme) } },
            )
        }
    }
    if (settings.themeId.darkOnly) {
        AppText(
            "This theme has no light version.",
            SettingsRowPadding.padding(bottom = 6.dp),
            style = Type.caption,
            color = Tokens.current.muted,
        )
    }
    Segmented(
        selection = if (settings.themeId.darkOnly) ColorSchemePreference.Dark else settings.colorScheme,
        options = listOf(
            ColorSchemePreference.System to "System",
            ColorSchemePreference.Light to "Light",
            ColorSchemePreference.Dark to "Dark",
        ),
        select = { choice -> appearance.update { it.copy(colorScheme = choice) } },
        enabled = !settings.themeId.darkOnly,
        modifier = SettingsRowPadding.padding(bottom = 8.dp),
    )
}

@Composable
private fun ThemeSwatchCard(
    label: String,
    colors: SimpleColors,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = SwatchShape
    val ring = if (selected) {
        Modifier.border(2.dp, Tokens.current.focusRing, shape)
    } else {
        Modifier.border(1.dp, Tokens.current.line, shape)
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(SwatchWidth)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = label },
    ) {
        Box(
            Modifier
                .width(SwatchWidth)
                .height(52.dp)
                .clip(shape)
                .then(ring)
                .background(colors.background, shape)
                .padding(10.dp),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(0.72f)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(colors.foreground),
            )
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(colors.primaryFill),
            )
        }
        AppText(
            label,
            Modifier.padding(top = 6.dp).fillMaxWidth(),
            style = Type.caption,
            color = if (selected) Tokens.current.foreground else Tokens.current.muted,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}
