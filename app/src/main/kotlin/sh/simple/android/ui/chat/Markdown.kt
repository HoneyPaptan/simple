package sh.simple.android.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.kyant.shapes.RoundedRectangle
import sh.simple.android.ui.kit.AppText
import sh.simple.android.ui.kit.CodeCard
import sh.simple.android.ui.theme.Tokens
import sh.simple.android.ui.theme.Type

sealed interface MdBlock {
    data class Paragraph(val text: String) : MdBlock
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Code(val lang: String, val body: String) : MdBlock
    data class ListItem(val text: String, val marker: String = "•") : MdBlock
    data class Table(val header: List<String>, val rows: List<List<String>>) : MdBlock
}

private val tableSeparator = Regex("""^\|?\s*:?-{2,}:?\s*(\|\s*:?-{2,}:?\s*)*\|?\s*$""")

private fun tableCells(line: String): List<String> =
    line.trim().removePrefix("|").removeSuffix("|").split("|").map { it.trim() }

private fun isTableRow(line: String): Boolean = line.trimStart().startsWith("|")

@Composable
fun rememberParsedMarkdown(messageKey: String, source: String): List<MdBlock> =
    produceState(initialValue = emptyList(), messageKey, source) {
        value = withContext(Dispatchers.Default) { parseMarkdown(source) }
    }.value

const val TableColumnMaxDp = 240

fun tableCellMinDp(cell: String, maxDp: Int = TableColumnMaxDp): Dp {
    val plain = cell.replace(Regex("""[*_`]+"""), "").trim().ifEmpty { " " }
    val estimate = (plain.length * 8).coerceAtLeast(32)
    return minOf(estimate, maxDp).dp
}

fun parseMarkdown(source: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val lines = source.lines()
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        if (line.startsWith("```")) {
            val lang = line.removePrefix("```").trim()
            val body = buildString {
                i++
                while (i < lines.size && !lines[i].startsWith("```")) {
                    appendLine(lines[i])
                    i++
                }
            }.trimEnd()
            blocks += MdBlock.Code(lang, body)
            i++
            continue
        }
        if (isTableRow(line) && i + 1 < lines.size && tableSeparator.matches(lines[i + 1].trim())) {
            val header = tableCells(line)
            val rows = mutableListOf<List<String>>()
            i += 2
            while (i < lines.size && isTableRow(lines[i])) {
                rows += tableCells(lines[i])
                i++
            }
            blocks += MdBlock.Table(header, rows)
            continue
        }
        if (line.startsWith("#")) {
            val level = line.takeWhile { it == '#' }.length
            blocks += MdBlock.Heading(level, line.drop(level).trim())
            i++
            continue
        }
        val trimmed = line.trimStart()
        if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
            blocks += MdBlock.ListItem(trimmed.drop(2).trim())
            i++
            continue
        }
        val numbered = Regex("""^(\d+[.)])\s+(.*)$""").find(trimmed)
        if (numbered != null) {
            blocks += MdBlock.ListItem(numbered.groupValues[2].trim(), numbered.groupValues[1])
            i++
            continue
        }
        if (line.isBlank()) {
            i++
            continue
        }
        blocks += MdBlock.Paragraph(line.trim())
        i++
    }
    return blocks
}

@Composable
fun MarkdownBody(blocks: List<MdBlock>, modifier: Modifier = Modifier, onCopy: (String) -> Unit = {}) {
    Column(modifier.fillMaxWidth(), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
        for (block in blocks) {
            when (block) {
                is MdBlock.Heading -> InlineText(
                    block.text,
                    style = if (block.level <= 1) Type.chatHeading else Type.headline,
                )
                is MdBlock.Paragraph -> InlineText(block.text, style = Type.chatBody)
                is MdBlock.ListItem -> Row {
                    AppText(block.marker, Modifier.padding(end = 8.dp), style = Type.chatBody)
                    InlineText(block.text, style = Type.chatBody)
                }
                is MdBlock.Code -> CodeCard(block.lang.ifEmpty { "code" }, "", "", block.body, { onCopy(block.body) })
                is MdBlock.Table -> {
                    androidx.compose.foundation.layout.Box(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                    ) {
                        MarkdownTable(block)
                    }
                }
            }
        }
    }
}

@Composable
private fun InlineText(text: String, style: androidx.compose.ui.text.TextStyle) {
    val colors = Tokens.current
    val annotated = remember(text, colors) {
        buildAnnotatedString {
            for (span in parseInline(text)) {
                val spanStyle = SpanStyle(
                    fontFamily = if (span.code) Type.code.fontFamily else null,
                    fontWeight = if (span.bold) FontWeight.SemiBold else null,
                    fontStyle = if (span.italic) FontStyle.Italic else null,
                    background = if (span.code) colors.raised else Color.Unspecified,
                    textDecoration = if (span.link != null) TextDecoration.Underline else null,
                )
                if (span.link != null) {
                    withLink(LinkAnnotation.Url(span.link)) { withStyle(spanStyle) { append(span.text) } }
                } else {
                    withStyle(spanStyle) { append(span.text) }
                }
            }
        }
    }
    BasicText(annotated, style = style.copy(color = colors.foreground))
}

@Composable
fun AssistantBubble(
    blocks: List<MdBlock>,
    modifier: Modifier = Modifier,
    onCopy: (String) -> Unit = {},
) {
    val shape = RoundedRectangle(20.dp)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val maxBubble = maxWidth - 32.dp
        Column(
            modifier
                .widthIn(max = maxBubble)
                .clip(shape)
                .background(Tokens.current.card)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            MarkdownBody(blocks, onCopy = onCopy)
        }
    }
}

@Composable
fun UserBubble(
    text: String,
    modifier: Modifier = Modifier,
    imagePaths: List<String> = emptyList(),
    statusLabel: String? = null,
    onOpenImage: (String) -> Unit = {},
) {
    val spoken = buildString {
        if (imagePaths.isNotEmpty()) append("Image attachment. ")
        if (text.isNotBlank()) append(text)
        if (!statusLabel.isNullOrBlank()) {
            if (isNotEmpty()) append(". ")
            append(statusLabel)
        }
    }
    val shape = RoundedRectangle(20.dp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        BoxWithConstraints {
            val maxBubble = maxWidth - 48.dp
            Column(
                modifier
                    .widthIn(max = maxBubble)
                    .semantics(mergeDescendants = true) {
                        if (spoken.isNotBlank()) contentDescription = spoken
                    }
                    .clip(shape)
                    .background(Tokens.current.userBubble)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                if (imagePaths.isNotEmpty()) {
                    Column(Modifier.padding(bottom = if (text.isNotBlank()) 8.dp else 0.dp)) {
                        for (path in imagePaths) {
                            ChatImageThumbnail(path, onOpenImage)
                        }
                    }
                }
                if (text.isNotBlank()) {
                    AppText(text, style = Type.chatBody, color = Tokens.current.userBubbleInk)
                }
                if (!statusLabel.isNullOrBlank()) {
                    AppText(
                        statusLabel,
                        Modifier.padding(top = 6.dp),
                        style = Type.meta,
                        color = Tokens.current.muted,
                    )
                }
            }
        }
    }
}

@Composable
private fun MarkdownTable(table: MdBlock.Table) {
    val colors = Tokens.current
    val columns = maxOf(table.header.size, table.rows.maxOfOrNull { it.size } ?: 0)
    val columnMins = (0 until columns).map { col ->
        val headerCell = table.header.getOrNull(col).orEmpty()
        val rowCells = table.rows.mapNotNull { it.getOrNull(col) }
        val cells = listOf(headerCell) + rowCells
        cells.maxOf { tableCellMinDp(it) }
    }
    Column(
        Modifier
            .clip(RoundedRectangle(12.dp))
            .background(colors.raised),
    ) {
        TableRow(table.header, columns, columnMins, header = true)
        table.rows.forEach { TableRow(it, columns, columnMins, header = false) }
    }
}

@Composable
private fun TableRow(
    cells: List<String>,
    columns: Int,
    columnMins: List<Dp>,
    header: Boolean,
) {
    val style = Type.chatBody
    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
        for (c in 0 until columns) {
            val text = cells.getOrNull(c).orEmpty()
            val render = if (header && text.isNotEmpty()) "**$text**" else text
            val min = columnMins.getOrElse(c) { tableCellMinDp(text) }
            Column(Modifier.widthIn(min = min).padding(end = 8.dp)) {
                TableInlineText(render, style)
            }
        }
    }
}

@Composable
private fun TableInlineText(text: String, style: androidx.compose.ui.text.TextStyle) {
    val colors = Tokens.current
    val annotated = remember(text, colors) {
        buildAnnotatedString {
            for (span in parseInline(text)) {
                val spanStyle = SpanStyle(
                    fontFamily = if (span.code) Type.code.fontFamily else null,
                    fontWeight = if (span.bold) FontWeight.SemiBold else null,
                    fontStyle = if (span.italic) FontStyle.Italic else null,
                    background = if (span.code) colors.raised else Color.Unspecified,
                    textDecoration = if (span.link != null) TextDecoration.Underline else null,
                )
                if (span.link != null) {
                    withLink(LinkAnnotation.Url(span.link)) { withStyle(spanStyle) { append(span.text) } }
                } else {
                    withStyle(spanStyle) { append(span.text) }
                }
            }
        }
    }
    BasicText(
        annotated,
        style = style.copy(color = colors.foreground),
        softWrap = false,
        overflow = TextOverflow.Clip,
        maxLines = if (text.contains('`')) 1 else Int.MAX_VALUE,
    )
}
