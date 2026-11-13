package sh.simple.android.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import sh.simple.android.store.TerminalThemeStore
import sh.simple.android.ui.glass.GlassStage
import sh.simple.android.ui.kit.AppText
import sh.simple.android.ui.kit.HeaderBar
import sh.simple.android.ui.kit.Icons
import sh.simple.android.ui.kit.SimpleIcon
import sh.simple.android.ui.kit.listScrollBottomPadding
import sh.simple.android.ui.theme.LocalAppearance
import sh.simple.android.ui.theme.Tokens
import sh.simple.android.ui.theme.Type
import sh.simple.android.ui.terminal.TerminalPalette
import sh.simple.android.ui.terminal.TerminalThemes

@Composable
fun TerminalThemeScreen(
    store: TerminalThemeStore,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appearance = LocalAppearance.current
    val systemDark = isSystemInDarkTheme()
    val appDark = appearance.resolvesDark(systemDark)
  val pickerRows = buildList {
        add(
            PickerRow(
                id = "match",
                label = TerminalThemes.labelFor("match"),
                palette = TerminalThemes.resolve("match", appDark),
                note = if (appDark) "Uses dark app colors" else "Uses light app colors",
            ),
        )
        for (theme in TerminalThemes.catalog) {
            add(PickerRow(theme.id, theme.label, theme, null))
        }
    }
    GlassStage(modifier.fillMaxSize().background(Tokens.current.background)) {
        Column(Modifier.fillMaxSize()) {
            HeaderBar(listOf(Icons.Back), "Terminal theme", emptyList(), onLeftClick = { _ -> onBack() })
            LazyColumn(contentPadding = listScrollBottomPadding()) {
                items(pickerRows, key = { it.id }) { row ->
                    ThemePickerRow(
                        row = row,
                        selected = store.themeId == row.id,
                        onClick = { store.setTheme(row.id) },
                    )
                }
            }
        }
    }
}

private data class PickerRow(
    val id: String,
    val label: String,
    val palette: TerminalPalette,
    val note: String?,
)

@Composable
private fun ThemePickerRow(row: PickerRow, selected: Boolean, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(31.dp), contentAlignment = Alignment.CenterStart) {
                SimpleIcon(Icons.Term, size = 19.dp, tint = Tokens.current.foreground)
            }
            Column(Modifier.weight(1f)) {
                AppText(row.label, style = Type.rowLabel, maxLines = 1)
                if (row.note != null) {
                    AppText(row.note, style = Type.caption, color = Tokens.current.muted, maxLines = 1)
                }
            }
            ThemeSwatches(row.palette)
            if (selected) {
                SimpleIcon(Icons.Check, size = 18.dp, tint = Tokens.current.foreground, modifier = Modifier.padding(start = 8.dp))
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(start = 51.dp)
                .height(1.dp)
                .background(Tokens.current.hairline),
        )
    }
}

@Composable
private fun ThemeSwatches(palette: TerminalPalette) {
    val sample = listOf(palette.background, palette.foreground, palette.ansi[1], palette.ansi[2], palette.ansi[4])
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(end = 8.dp),
    ) {
        for (hex in sample) {
            Swatch(hex)
        }
    }
}

@Composable
private fun Swatch(hex: String) {
    val shape = RoundedCornerShape(4.dp)
    Box(
        Modifier
            .size(16.dp)
            .background(Color(android.graphics.Color.parseColor(hex)), shape)
            .border(1.dp, Tokens.current.line, shape),
    )
}
