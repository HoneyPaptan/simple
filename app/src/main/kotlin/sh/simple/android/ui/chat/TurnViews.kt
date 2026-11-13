package sh.simple.android.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.kyant.shapes.RoundedRectangle
import sh.simple.android.ui.kit.AppText
import sh.simple.android.ui.kit.Icons
import sh.simple.android.ui.kit.MinTouchTarget
import sh.simple.android.ui.kit.SimpleIcon
import sh.simple.android.ui.theme.Tokens
import sh.simple.android.ui.theme.Type
import sh.simple.android.ui.util.agentKindIcon
import sh.simple.android.ui.util.agentKindLabel
import java.io.File


@Composable
fun TurnHeader(kind: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        SimpleIcon(agentKindIcon(kind), size = 16.dp, tint = Tokens.current.muted, contentDescription = null)
        AppText(
            agentKindLabel(kind),
            Modifier.padding(start = 8.dp),
            style = Type.meta,
            color = Tokens.current.muted,
        )
    }
}

@Composable
fun WorkedPill(
    label: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = if (expanded) "expanded" else "collapsed"
    PressChip(
        onClick = onToggle,
        modifier.semantics {
            contentDescription = label
            stateDescription = state
        },
    ) {
        AppText(label, style = Type.toolLine, color = Tokens.current.muted, maxLines = 1)
        SimpleIcon(Icons.ChevR, size = 14.dp, tint = Tokens.current.faint, contentDescription = null)
    }
}

@Composable
fun FilesChangedCard(
    files: List<TurnFileChange>,
    modifier: Modifier = Modifier,
    onReview: () -> Unit,
) {
    if (files.isEmpty()) return
    val shape = RoundedRectangle(16.dp)
    val totalAdds = files.sumOf { it.adds }
    val totalRemoves = files.sumOf { it.removes }
    val summary = plural(files.size, "file") + " changed"
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Tokens.current.card)
            .border(0.5.dp, Tokens.current.hairline, shape),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppText(summary, Modifier.weight(1f), style = Type.toolLine, color = Tokens.current.foreground)
            if (totalAdds > 0) {
                AppText("+$totalAdds", style = Type.caption, color = Tokens.current.add)
            }
            if (totalRemoves > 0) {
                AppText(" −$totalRemoves", style = Type.caption, color = Tokens.current.remove)
            }
            Row(
                Modifier
                    .padding(start = 12.dp)
                    .clickable(role = Role.Button, onClick = onReview),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppText("Review", style = Type.toolLine, color = Tokens.current.muted)
                SimpleIcon(Icons.ChevR, size = 14.dp, tint = Tokens.current.faint, contentDescription = null)
            }
        }
        Column(
            Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            files.forEach { FileChangedRow(it) }
        }
    }
}

@Composable
private fun FileChangedRow(file: TurnFileChange) {
    val name = File(file.path).name
    val folder = File(file.path).parent.orEmpty().replace(File.separatorChar, '/')
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SimpleIcon(Icons.File, size = 16.dp, tint = Tokens.current.muted, contentDescription = null)
        BasicText(
            buildAnnotatedString {
                if (folder.isNotEmpty()) {
                    withStyle(SpanStyle(color = Tokens.current.muted)) { append("$folder/") }
                }
                withStyle(SpanStyle(color = Tokens.current.foreground, fontWeight = FontWeight.Medium)) { append(name) }
            },
            Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
            style = Type.meta,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (file.adds > 0) AppText("+${file.adds}", style = Type.caption, color = Tokens.current.add)
        if (file.removes > 0) AppText(" −${file.removes}", style = Type.caption, color = Tokens.current.remove)
    }
}

@Composable
private fun PressChip(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val shape = RoundedCornerShape(999.dp)
    Row(
        modifier
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = MinTouchTarget)
            .clip(shape)
            .background(Tokens.current.card)
            .border(0.5.dp, Tokens.current.hairline, shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

private fun plural(n: Int, one: String, many: String = "${one}s"): String = "$n ${if (n == 1) one else many}"

@Composable
fun TurnRowView(
    row: TurnRow,
    agentKind: String,
    displayBlocks: List<ChatBlock>,
    working: Boolean,
    copyModifier: (String) -> Modifier,
    onOpenImage: (String) -> Unit,
    onToggleWorked: (String) -> Unit,
    onReviewChanges: () -> Unit,
) {
    val top = Modifier.padding(top = row.topGap.dp)
    when (row) {
        is TurnRow.User -> UserBubble(
            row.block.text,
            copyModifier(row.block.text).then(top),
            row.block.imagePaths,
            statusLabel = row.block.statusLabel,
            onOpenImage = onOpenImage,
        )
        is TurnRow.Header -> TurnHeader(agentKind, top)
        is TurnRow.Worked -> Box(top) { WorkedPill(row.label, row.open, { onToggleWorked(row.turnKey) }) }
        is TurnRow.Files -> Box(top) { FilesChangedCard(row.files, onReview = onReviewChanges) }
        is TurnRow.Body -> Box(top) { TurnBodyBlock(row.block, displayBlocks, working, copyModifier) }
    }
}

@Composable
fun TurnBodyBlock(
    block: ChatBlock,
    displayBlocks: List<ChatBlock>,
    working: Boolean,
    copyModifier: (String) -> Modifier,
) {
    when (block) {
        is ChatBlock.Assistant -> {
            val md = rememberParsedMarkdown(block.key, block.text)
            Box(copyModifier(block.text)) { AssistantBubble(md) }
        }
        is ChatBlock.Tools -> ToolGroupView(block, live = working && block === displayBlocks.lastOrNull())
        is ChatBlock.Thinking -> ThinkingView(block, live = working && block === displayBlocks.lastOrNull())
        is ChatBlock.Marker -> MarkerLine(block.text)
        is ChatBlock.User -> Unit
    }
}
