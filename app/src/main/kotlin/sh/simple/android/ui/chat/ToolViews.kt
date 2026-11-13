package sh.simple.android.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.BasicText
import com.kyant.shapes.RoundedRectangle
import sh.simple.android.ui.glass.rememberPress
import sh.simple.android.ui.kit.AppText
import sh.simple.android.ui.kit.Icons
import sh.simple.android.ui.kit.MinTouchTarget
import sh.simple.android.ui.kit.ShimmerText
import sh.simple.android.ui.kit.SimpleIcon
import sh.simple.android.ui.kit.DotGridLoader
import sh.simple.android.ui.theme.LocalCodeStyle
import sh.simple.android.ui.theme.Tokens
import sh.simple.android.ui.theme.Type

private const val OUTPUT_LINES = 24

private fun toolAccessibilityLabel(tool: ToolEntry): String {
    val detail = tool.detail.ifBlank { tool.title }
    val state = when {
        tool.running -> "running"
        tool.failed -> "failed"
        else -> "done"
    }
    return "${tool.verb} $detail, $state".trim()
}

@Composable
fun ToolGroupView(block: ChatBlock.Tools, live: Boolean, modifier: Modifier = Modifier) {
    var open by rememberSaveable(block.key) { mutableStateOf(false) }
    val current = if (live) liveTool(block.tools) else null
    val summary = if (current != null) toolAccessibilityLabel(current) else toolGroupLabel(block.tools)
    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .semantics(mergeDescendants = true) { contentDescription = summary },
    ) {
        PressRow(onClick = { open = !open }, label = summary) {
            if (current != null) {
                LiveLabel(current.title, "", Modifier.weight(1f))
            } else {
                GroupLine(toolGroupLabel(block.tools), Modifier.weight(1f))
            }
            SimpleIcon(
                if (open) Icons.ChevD else Icons.ChevR,
                size = 16.dp,
                tint = Tokens.current.faint,
                contentDescription = null,
            )
        }
        if (open) {
            Column(
                Modifier.padding(start = 2.dp, top = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                block.tools.forEach { ToolRowView(it) }
            }
        }
    }
}

@Composable
fun ThinkingView(block: ChatBlock.Thinking, live: Boolean, modifier: Modifier = Modifier) {
    var open by rememberSaveable(block.key) { mutableStateOf(false) }
    val summary = if (live) "Thinking" else "Thought, ${block.text.lineSequence().first()}"
    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .semantics(mergeDescendants = true) { contentDescription = summary },
    ) {
        PressRow(onClick = { open = !open }, label = summary) {
            if (live) {
                LiveLabel("Thinking", "", Modifier.weight(1f))
            } else {
                VerbLine("Thought", if (open) "" else block.text.lineSequence().first(), Modifier.weight(1f), Type.toolLine)
            }
            SimpleIcon(
                if (open) Icons.ChevD else Icons.ChevR,
                size = 16.dp,
                tint = Tokens.current.faint,
                contentDescription = null,
            )
        }
        if (open) {
            AppText(block.text, Modifier.padding(top = 4.dp, bottom = 6.dp), style = Type.meta, color = Tokens.current.muted)
        }
    }
}

@Composable
fun MarkerLine(text: String, modifier: Modifier = Modifier) {
    AppText(text, modifier.fillMaxWidth().padding(vertical = 8.dp), style = Type.caption, color = Tokens.current.faint)
}

@Composable
fun WorkingLine(label: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        LiveLabel("Working", label, Modifier.weight(1f))
    }
}

@Composable
private fun ToolRowView(tool: ToolEntry) {
    var open by rememberSaveable(tool.id) { mutableStateOf(false) }
    val expandable = hasDetail(tool)
    Column(Modifier.fillMaxWidth()) {
        val lineLabel = toolAccessibilityLabel(tool)
        PressRow(onClick = { if (expandable) open = !open }, label = lineLabel) {
            VerbLine(tool.verb, tool.detail, Modifier.weight(1f), Type.meta)
            when {
                tool.running -> DotGridLoader(Tokens.current.foreground, modifier = Modifier.padding(start = 6.dp))
                tool.failed -> AppText("Failed", style = Type.caption, color = Tokens.current.remove)
            }
            if (tool.kind == ToolKind.Edit) DiffCount(tool.diff)
        }
        if (open) ToolDetail(tool, Modifier.padding(top = 4.dp, bottom = 8.dp))
    }
}

@Composable
private fun ToolDetail(tool: ToolEntry, modifier: Modifier) {
    val shape = RoundedRectangle(14.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Tokens.current.codeBackground)
            .border(1.dp, Tokens.current.line, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when {
            tool.todos.isNotEmpty() -> tool.todos.forEach { TodoRow(it) }
            tool.kind == ToolKind.Edit && tool.diff.isNotEmpty() -> DiffBody(tool.diff)
            else -> {
                if (tool.command.isNotEmpty()) CodeText("$ " + tool.command, Tokens.current.muted)
                if (tool.output.isNotEmpty()) CodeText(clipLines(tool.output), Tokens.current.foreground)
            }
        }
    }
}

@Composable
private fun DiffBody(diff: String) {
    Column(Modifier.horizontalScroll(rememberScrollState())) {
        diffLines(diff).take(OUTPUT_LINES * 2).forEach { (sign, text) ->
            val fill = if (sign == '+') Tokens.current.diffAddBackground else Tokens.current.diffRemoveBackground
            AppText("$sign $text", Modifier.background(fill), style = LocalCodeStyle.current, maxLines = 1)
        }
    }
}

@Composable
private fun TodoRow(item: TodoItem) {
    val done = item.status == "completed"
    val active = item.status == "in_progress"
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (done) {
            SimpleIcon(Icons.Check, size = 14.dp, tint = Tokens.current.add)
        } else {
            androidx.compose.foundation.layout.Box(
                Modifier
                    .padding(2.dp)
                    .size(10.dp)
                    .border(1.5.dp, if (active) Tokens.current.foreground else Tokens.current.faint, CircleShape),
            )
        }
        AppText(
            item.text,
            Modifier.padding(start = 8.dp),
            style = Type.meta,
            color = if (active) Tokens.current.foreground else Tokens.current.muted,
        )
    }
}

@Composable
private fun CodeText(text: String, color: androidx.compose.ui.graphics.Color) {
    AppText(
        text,
        Modifier.horizontalScroll(rememberScrollState()),
        style = LocalCodeStyle.current,
        color = color,
    )
}

@Composable
private fun DiffCount(diff: String) {
    val lines = diffLines(diff)
    val adds = lines.count { it.first == '+' }
    val removes = lines.count { it.first == '-' }
    if (adds == 0 && removes == 0) return
    Row(Modifier.padding(start = 8.dp)) {
        if (adds > 0) AppText("+$adds", style = Type.caption, color = Tokens.current.add)
        if (removes > 0) AppText(" -$removes", style = Type.caption, color = Tokens.current.remove)
    }
}

@Composable
private fun VerbLine(verb: String, detail: String, modifier: Modifier, style: androidx.compose.ui.text.TextStyle) {
    val muted = Tokens.current.muted
    val faint = Tokens.current.faint
    BasicText(
        buildAnnotatedString {
            withStyle(SpanStyle(color = muted)) { append(verb) }
            if (detail.isNotEmpty() && detail != verb) {
                withStyle(SpanStyle(color = faint)) { append(" $detail") }
            }
        },
        modifier,
        style = style,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun GroupLine(label: String, modifier: Modifier) {
    val verb = label.substringBefore(' ')
    val muted = Tokens.current.muted
    val faint = Tokens.current.faint
    BasicText(
        buildAnnotatedString {
            withStyle(SpanStyle(color = muted)) { append(verb) }
            withStyle(SpanStyle(color = faint)) { append(label.removePrefix(verb)) }
        },
        modifier,
        style = Type.toolLine,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun LiveLabel(verb: String, detail: String, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (verb == "Working") {
            DotGridLoader(Tokens.current.foreground, modifier = Modifier.padding(end = 6.dp))
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                ShimmerText("Working", style = Type.toolLine, baseColor = Tokens.current.foreground)
                if (detail.isNotEmpty()) {
                    VerbLine("", detail, Modifier, Type.toolLine)
                }
            }
        } else {
            VerbLine(verb, detail, Modifier.weight(1f), Type.toolLine)
        }
    }
}

@Composable
private fun PressRow(
    onClick: () -> Unit,
    label: String,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val (source, pressed) = rememberPress()
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (pressed) 0.5f else 1f }
            .semantics { contentDescription = label }
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
            .heightIn(min = MinTouchTarget)
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

private fun hasDetail(tool: ToolEntry): Boolean =
    tool.todos.isNotEmpty() || tool.diff.isNotEmpty() || tool.output.isNotEmpty() || tool.command.isNotEmpty()

private fun clipLines(text: String): String {
    val lines = text.lines()
    if (lines.size <= OUTPUT_LINES) return text
    return lines.take(OUTPUT_LINES).joinToString("\n") + "\n${lines.size - OUTPUT_LINES} more lines"
}
