package sh.simple.android.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import sh.simple.android.ui.glass.rememberPress
import sh.simple.android.ui.theme.LocalCodeStyle
import sh.simple.android.ui.theme.Tokens
import sh.simple.android.ui.theme.Type
import sh.simple.android.ui.theme.medium

@Composable
fun CodeCard(
    lang: String,
    file: String,
    lineRange: String,
    code: String,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedRectangle(18.dp)
    val displayRange = formatCodeLineRange(lineRange)
    val codeStyle = LocalCodeStyle.current
    val faint = Tokens.current.faint
    val foreground = Tokens.current.foreground
    val gutterText = remember(code) {
        val count = code.count { it == '\n' } + 1
        buildString {
            for (n in 1..count) {
                if (n > 1) append('\n')
                append(n)
            }
        }
    }
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Tokens.current.codeBackground)
            .border(1.dp, Tokens.current.line, shape)
            .padding(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AppText(lang, style = Type.caption, color = Tokens.current.muted)
            AppText(file, Modifier.padding(start = 8.dp).weight(1f), style = Type.caption, maxLines = 1)
            AppText(displayRange, style = Type.caption, color = Tokens.current.muted)
            Box(
                Modifier
                    .padding(start = 8.dp)
                    .minTouchTarget()
                    .clickable(role = Role.Button, onClick = onCopy),
                contentAlignment = Alignment.Center,
            ) {
                SimpleIcon(Icons.Copy, size = 18.dp, tint = Tokens.current.muted, contentDescription = "Copy code")
            }
        }
        Row(Modifier.padding(top = 8.dp)) {
            BasicText(
                gutterText,
                Modifier.padding(end = 10.dp),
                style = codeStyle.copy(color = faint),
            )
            BasicText(
                code,
                Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                style = codeStyle.copy(color = foreground),
                softWrap = false,
            )
        }
    }
}

private fun formatCodeLineRange(raw: String): String {
    val parts = raw.split(":")
    if (parts.size == 2 && parts[0].all(Char::isDigit) && parts[1].all(Char::isDigit)) {
        return "L${parts[0]}-${parts[1]}"
    }
    return raw
}

@Composable
fun DiffLines(
    adds: List<String>,
    removes: List<String>,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (line in removes) {
            Row(Modifier.fillMaxWidth().background(Tokens.current.diffRemoveBackground)) {
                AppText(line, style = LocalCodeStyle.current, color = Tokens.current.foreground, maxLines = 1)
            }
        }
        for (line in adds) {
            Row(Modifier.fillMaxWidth().background(Tokens.current.diffAddBackground)) {
                AppText(line, style = LocalCodeStyle.current, color = Tokens.current.foreground, maxLines = 1)
            }
        }
    }
}

@Composable
fun ChangesCard(
    changeCount: Int,
    files: List<ChangeFileRow>,
    moreCount: Int = 0,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedRectangle(18.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Tokens.current.card)
            .border(1.dp, Tokens.current.line, shape)
            .padding(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AppText("Changes", Modifier.weight(1f), style = Type.headline, maxLines = 1)
            AppText("$changeCount", style = Type.meta, color = Tokens.current.muted)
        }
        files.forEach { row ->
            ChangeFileRowView(row, Modifier.padding(top = 10.dp))
        }
        if (moreCount > 0) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SimpleIcon(Icons.More, size = 18.dp, tint = Tokens.current.muted)
                AppText("$moreCount more", style = Type.meta, color = Tokens.current.muted)
            }
        }
    }
}

data class ChangeFileRow(
    val langTag: String,
    val fileName: String,
    val directory: String = "",
    val add: Int,
    val remove: Int,
)

@Composable
private fun ChangeFileRowView(row: ChangeFileRow, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        AppText(row.langTag, style = Type.caption, color = Tokens.current.muted, maxLines = 1)
        Column(Modifier.padding(start = 8.dp).weight(1f)) {
            AppText(row.fileName, style = Type.body, maxLines = 1)
            if (row.directory.isNotEmpty()) {
                AppText(row.directory, style = Type.meta, color = Tokens.current.muted, maxLines = 1)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (row.add > 0) {
                AppText("+${row.add}", style = Type.meta, color = Tokens.current.add)
            }
            if (row.remove > 0) {
                AppText("-${row.remove}", style = Type.meta, color = Tokens.current.remove)
            }
        }
    }
}

@Composable
fun ActionCard(
    title: String,
    subtitle: String,
    monoBlock: String,
    primary: String,
    secondary: String,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
    link: String? = null,
    onLink: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedRectangle(20.dp)
    val commandShape = RoundedRectangle(12.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Tokens.current.card)
            .border(1.dp, Tokens.current.line, shape)
            .semantics {
                customActions = buildList {
                    add(CustomAccessibilityAction(primary) { onPrimary(); true })
                    add(CustomAccessibilityAction(secondary) { onSecondary(); true })
                    if (link != null && onLink != null) {
                        add(CustomAccessibilityAction(link) { onLink(); true })
                    }
                }
            }
            .padding(14.dp),
    ) {
        AppText(title, style = Type.headline, maxLines = 2)
        if (subtitle.isNotEmpty()) {
            AppText(subtitle, Modifier.padding(top = 4.dp), style = Type.meta, color = Tokens.current.muted, maxLines = 3)
        }
        AppText(
            monoBlock,
            Modifier
                .padding(top = 10.dp)
                .fillMaxWidth()
                .clip(commandShape)
                .background(Tokens.current.raised)
                .padding(10.dp),
            style = LocalCodeStyle.current,
            maxLines = 8,
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ActionCardButton(secondary, primary = false, Modifier.weight(1f), onSecondary)
            ActionCardButton(primary, primary = true, Modifier.weight(1f), onPrimary)
        }
        if (link != null && onLink != null) {
            ActionCardButton(link, primary = false, Modifier.fillMaxWidth().padding(top = 8.dp), onLink)
        }
    }
}

@Composable
private fun ActionCardButton(
    title: String,
    primary: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val (source, pressed) = rememberPress()
    Box(
        modifier
            .pressScale(pressed)
            .heightIn(min = MinTouchTarget)
            .clip(Capsule())
            .background(if (primary) Tokens.current.primaryFill else Tokens.current.raised)
            .clickable(source, null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AppText(
            title,
            style = Type.rowLabel.medium,
            color = if (primary) Tokens.current.onPrimary else Tokens.current.foreground,
            maxLines = 1,
        )
    }
}

@Composable
fun ChoiceCard(
    title: String,
    body: String,
    options: List<String>,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    CardShell(title, body, modifier.semantics {
        customActions = options.mapIndexed { index, option ->
            CustomAccessibilityAction(option) { onPick(index); true }
        }
    }) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEachIndexed { index, option ->
                SecondaryButton(option, Modifier.fillMaxWidth(), maxLines = 3) { onPick(index) }
            }
        }
    }
}

@Composable
fun PlanCard(
    title: String,
    body: String,
    onApprove: () -> Unit,
    onKeepPlanning: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CardShell(
        title,
        body,
        modifier.semantics {
            customActions = listOf(
                CustomAccessibilityAction("Approve") { onApprove(); true },
                CustomAccessibilityAction("Keep planning") { onKeepPlanning(); true },
            )
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("Keep planning", Modifier.weight(1f)) { onKeepPlanning() }
            PrimaryButton("Approve", Modifier.weight(1f)) { onApprove() }
        }
    }
}

@Composable
fun YesNoCard(
    title: String,
    body: String,
    onYes: () -> Unit,
    onNo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CardShell(title, body, modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("No", Modifier.weight(1f)) { onNo() }
            PrimaryButton("Yes", Modifier.weight(1f)) { onYes() }
        }
    }
}

@Composable
fun ContinueCard(
    title: String,
    body: String,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CardShell(title, body, modifier) {
        PrimaryButton("Continue") { onContinue() }
    }
}

@Composable
fun LinkCard(
    title: String,
    body: String,
    url: String,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CardShell(title, body, modifier) {
        PrimaryButton("Open link") { onOpen() }
        AppText(url, Modifier.padding(top = 8.dp), style = Type.meta, color = Tokens.current.muted, maxLines = 2)
    }
}

@Composable
fun InputCard(
    title: String,
    body: String,
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CardShell(title, body, modifier) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = Type.body.copy(color = Tokens.current.foreground),
            cursorBrush = SolidColor(Tokens.current.foreground),
            singleLine = true,
        )
        PrimaryButton("Send", Modifier.padding(top = 8.dp)) { onSend() }
    }
}

@Composable
fun ErrorCard(
    title: String,
    body: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CardShell(title, body, modifier) {
        PrimaryButton("Retry") { onRetry() }
    }
}

@Composable
fun SentCard(title: String, body: String, modifier: Modifier = Modifier) {
    CardShell(title, body, modifier) {
        AppText("Sent", style = Type.meta, color = Tokens.current.muted)
    }
}

@Composable
fun ScreenCard(
    liveText: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val shape = RoundedRectangle(18.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .background(Tokens.current.card)
            .border(1.dp, Tokens.current.line, shape)
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .padding(top = 4.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(Tokens.current.add),
        )
        AppText(liveText, Modifier.padding(start = 10.dp).weight(1f), style = LocalCodeStyle.current, maxLines = 12)
    }
}

@Composable
private fun CardShell(
    title: String,
    subtitle: String,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    val shape = RoundedRectangle(20.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Tokens.current.card)
            .border(1.dp, Tokens.current.line, shape)
            .padding(14.dp),
    ) {
        AppText(title, Modifier.semantics { heading() }, style = Type.headline, maxLines = 3)
        if (subtitle.isNotEmpty()) {
            AppText(
                subtitle,
                Modifier.padding(top = 8.dp),
                style = Type.meta,
                color = Tokens.current.muted,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(Modifier.padding(top = 14.dp)) { content() }
    }
}
