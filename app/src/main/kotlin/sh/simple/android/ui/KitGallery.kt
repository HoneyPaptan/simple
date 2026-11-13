package sh.simple.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import sh.simple.android.ui.glass.GlassStage
import sh.simple.android.ui.kit.ActionCard
import sh.simple.android.ui.kit.AgentRow
import sh.simple.android.ui.kit.AnchoredMenu
import sh.simple.android.ui.kit.AppText
import sh.simple.android.ui.kit.CenterDialog
import sh.simple.android.ui.kit.ChangeFileRow
import sh.simple.android.ui.kit.ChangesCard
import sh.simple.android.ui.kit.ChoiceCard
import sh.simple.android.ui.kit.CodeCard
import sh.simple.android.ui.kit.ComposerPill
import sh.simple.android.ui.kit.DialogButtonSpec
import sh.simple.android.ui.kit.KitGalleryComposerSection
import sh.simple.android.ui.kit.GlassSheet
import sh.simple.android.ui.kit.GlassSlider
import sh.simple.android.ui.kit.HeaderBar
import sh.simple.android.ui.kit.Icons
import sh.simple.android.ui.kit.LargeTitle
import sh.simple.android.ui.kit.ListRow
import sh.simple.android.ui.kit.MenuEntry
import sh.simple.android.ui.kit.PrimaryButton
import sh.simple.android.ui.kit.ScreenCard
import sh.simple.android.ui.kit.SecondaryButton
import sh.simple.android.ui.kit.SectionLabel
import sh.simple.android.ui.kit.Segmented
import sh.simple.android.ui.glass.GlassBudgetDebug
import sh.simple.android.ui.kit.Toggle
import sh.simple.android.ui.theme.LocalSimpleColors
import sh.simple.android.ui.theme.SimpleColors
import sh.simple.android.ui.theme.Tokens
import sh.simple.android.ui.theme.Type

@Composable
fun KitGallery(modifier: Modifier = Modifier, onExit: () -> Unit = {}) {
    var previewDark by remember { mutableStateOf(false) }
    val previewColors = if (previewDark) SimpleColors.Dark else SimpleColors.Light
    CompositionLocalProvider(LocalSimpleColors provides previewColors) {
        KitGalleryContent(modifier, onExit, previewDark, { previewDark = it })
    }
}

@Composable
private fun KitGalleryContent(
    modifier: Modifier,
    onExit: () -> Unit,
    previewDark: Boolean,
    onPreviewDark: (Boolean) -> Unit,
) {
    var sheetOpen by remember { mutableStateOf(false) }
    var stackSheet by remember { mutableStateOf(false) }
    var dialogOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var menuAnchor by remember { mutableStateOf(Rect.Zero) }
    var segment by remember { mutableStateOf("A") }
    var toggleOn by remember { mutableStateOf(true) }
    var slider by remember { mutableStateOf(28) }
    var dialogField by remember { mutableStateOf("") }

    GlassStage(
        modifier.fillMaxSize().background(Tokens.current.background),
        chrome = {
            ComposerPill(
                "Message simple",
                onPlus = {},
                onMic = {},
                onTap = {},
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 30.dp),
            )
        },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 100.dp),
        ) {
            HeaderBar(
                left = listOf(Icons.Back),
                title = "Kit gallery",
                subtitle = "Debug",
                right = listOf(Icons.Grid, Icons.More),
                onLeftClick = { onExit() },
            )
            Segmented(
                selection = previewDark,
                options = listOf(false to "Light", true to "Dark"),
                select = onPreviewDark,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            Toggle(
                checked = GlassBudgetDebug.overlayEnabled,
                onChange = { GlassBudgetDebug.overlayEnabled = it },
                modifier = Modifier.padding(horizontal = 20.dp),
            ) {
                AppText("Glass budget overlay", style = Type.body)
            }
            LargeTitle("Components")
            SectionLabel("Lists")
            ListRow(Icons.Folders, "All agents", trailing = "7")
            AgentRow(
                "claude",
                "Add retry with backoff",
                sh.simple.android.ui.util.agentStatusLine("waiting", "approval", null, System.currentTimeMillis()),
                "",
            )
            SectionLabel("Composer")
            KitGalleryComposerSection()
            SectionLabel("Controls")
            PrimaryButton("Primary", Modifier.padding(horizontal = 20.dp)) {}
            SecondaryButton("Secondary", Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {}
            Toggle(checked = toggleOn, onChange = { toggleOn = it }, modifier = Modifier.padding(horizontal = 20.dp)) {
                AppText("Notifications", style = Type.body)
            }
            Segmented(
                selection = segment,
                options = listOf("A" to "Ask", "B" to "Agent", "C" to "Plan"),
                select = { segment = it },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            GlassSlider("Blur", slider, 0..40, { slider = it }, Modifier.padding(horizontal = 20.dp))
            SectionLabel("Cards")
            CodeCard("go", "client.go", "12:40", "func retry() error {\n  return nil\n}", {}, Modifier.padding(horizontal = 20.dp))
            ChangesCard(
                changeCount = 6,
                files = listOf(
                    ChangeFileRow("GO", "retry.go", add = 120, remove = 0),
                    ChangeFileRow("GO", "http.go", add = 18, remove = 6),
                    ChangeFileRow("GO", "retry_test.go", add = 96, remove = 0),
                    ChangeFileRow("MD", "README.md", add = 9, remove = 1),
                ),
                moreCount = 2,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            ActionCard(
                "Run tests",
                "Agent wants shell access",
                "npm test",
                "Allow",
                "Deny",
                {},
                {},
                link = "View command",
                onLink = {},
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            ChoiceCard(
                "Pick a model",
                "Used for this agent",
                listOf("Opus", "Sonnet", "Haiku"),
                {},
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            ScreenCard("terminal$ git status\nOn branch main", Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            SectionLabel("Overlays")
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("Open sheet", onClick = { sheetOpen = true })
                SecondaryButton("Open dialog", onClick = { dialogOpen = true })
                Box(Modifier.onGloballyPositioned { menuAnchor = it.boundsInRoot() }) {
                    SecondaryButton("Open menu", onClick = { menuOpen = true })
                }
            }
        }
    }

    GlassSheet(sheetOpen, { sheetOpen = false }, "Workspace") {
        AppText("Half height sheet content", Modifier.padding(16.dp))
        SecondaryButton("Stack another") { stackSheet = true }
    }
    GlassSheet(
        stackSheet,
        { stackSheet = false },
        "Nested",
        doneLabel = "Done",
        onDone = { stackSheet = false },
        stacked = true,
    ) {
        AppText("Stacked sheet", Modifier.padding(16.dp))
    }
    CenterDialog(
        title = "Delete workspace?",
        visible = dialogOpen,
        onDismiss = { dialogOpen = false },
        body = "This cannot be undone.",
        field = dialogField,
        onFieldChange = { dialogField = it },
        buttons = listOf(
            DialogButtonSpec("Delete", destructive = true) {},
            DialogButtonSpec("Keep") {},
        ),
    )
    AnchoredMenu(
        open = menuOpen,
        onDismiss = { menuOpen = false },
        anchorBounds = menuAnchor,
        items = listOf(
            MenuEntry("Copy", Icons.Copy, action = {}),
            MenuEntry("Archive", Icons.Folder, detail = "Soon", action = {}),
            MenuEntry("Remove", Icons.Trash, destructive = true, action = {}),
        ),
    )
}
