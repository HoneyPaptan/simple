package sh.simple.android.ui.composer

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import sh.simple.android.ui.glass.GlassStage
import sh.simple.android.ui.kit.CenterDialog
import sh.simple.android.ui.kit.DialogButtonSpec
import sh.simple.android.ui.kit.CircleButton
import sh.simple.android.ui.kit.HeaderBar
import sh.simple.android.ui.kit.Icons
import sh.simple.android.ui.theme.Tokens

@Composable
fun ImageMarkupScreen(
    attachment: ComposerAttachment,
    onDone: () -> Unit,
    onCancel: () -> Unit,
) {
    val bitmap = remember(attachment.localPath) {
        BitmapFactory.decodeFile(attachment.localPath)?.asImageBitmap()
    }
    val (imageW, imageH) = remember(attachment.localPath) { imageSize(attachment.localPath) }
    var tool by remember { mutableStateOf(MarkupTool.Pen) }
    val themeColors = Tokens.current
    var color by remember(themeColors) { mutableStateOf(markupPalette(themeColors)[0].second) }
    var scale by remember { mutableStateOf(1f) }
    var panX by remember { mutableStateOf(0f) }
    var panY by remember { mutableStateOf(0f) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val undo = remember { mutableStateListOf<MarkupDocument>() }
    val redo = remember { mutableStateListOf<MarkupDocument>() }
    var doc by remember { mutableStateOf(MarkupDocument()) }
    var textOpen by remember { mutableStateOf(false) }
    var textField by remember { mutableStateOf("") }
    var textAt by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var penPoints by remember { mutableStateOf<List<Pair<Float, Float>>>(emptyList()) }
    var dragStart by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var dragEnd by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    fun pushDoc(next: MarkupDocument) {
        undo.add(doc)
        redo.clear()
        doc = next
    }
    fun mapTouch(x: Float, y: Float): Pair<Float, Float> = mapPoint(
        imageW,
        imageH,
        viewSize.width.toFloat(),
        viewSize.height.toFloat(),
        x,
        y,
        scale,
        panX,
        panY,
    )
    val cropStroke = Tokens.current.primaryFill
    GlassStage(
        Modifier
            .fillMaxSize()
            .background(Tokens.current.background),
        chrome = {
            HeaderBar(
                listOf(Icons.Back),
                "Markup",
                listOf(Icons.Check),
                onLeftClick = { _ -> onCancel() },
                onRightClick = {
                    flattenMarkupToPng(attachment.localPath, doc)
                    onDone()
                },
            )
        },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = 52.dp),
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onSizeChanged { viewSize = it }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 4f)
                            panX += pan.x
                            panY += pan.y
                        }
                    }
                    .pointerInput(tool, viewSize, scale, panX, panY) {
                        detectDragGestures(
                            onDragStart = { start ->
                                val p = mapTouch(start.x, start.y)
                                dragStart = p
                                dragEnd = p
                                if (tool == MarkupTool.Pen) penPoints = listOf(p)
                            },
                            onDrag = { change, _ ->
                                val p = mapTouch(change.position.x, change.position.y)
                                dragEnd = p
                                if (tool == MarkupTool.Pen) penPoints = penPoints + p
                            },
                            onDragEnd = {
                                val start = dragStart
                                val end = dragEnd
                                if (start != null && end != null) {
                                    when (tool) {
                                        MarkupTool.Pen -> {
                                            if (penPoints.size >= 2) {
                                                pushDoc(
                                                    doc.copy(
                                                        strokes = doc.strokes + MarkupStroke(
                                                            penPoints,
                                                            colorToArgb(color),
                                                            4f,
                                                        ),
                                                    ),
                                                )
                                            }
                                        }
                                        MarkupTool.Arrow -> {
                                            pushDoc(
                                                doc.copy(
                                                    arrows = doc.arrows + MarkupArrow(
                                                        start.first,
                                                        start.second,
                                                        end.first,
                                                        end.second,
                                                        colorToArgb(color),
                                                        4f,
                                                    ),
                                                ),
                                            )
                                        }
                                        MarkupTool.Rectangle -> {
                                            pushDoc(
                                                doc.copy(
                                                    rects = doc.rects + MarkupRect(
                                                        minOf(start.first, end.first),
                                                        minOf(start.second, end.second),
                                                        maxOf(start.first, end.first),
                                                        maxOf(start.second, end.second),
                                                        colorToArgb(color),
                                                        4f,
                                                    ),
                                                ),
                                            )
                                        }
                                        MarkupTool.Text -> {
                                            textAt = start
                                            textField = ""
                                            textOpen = true
                                        }
                                    }
                                }
                                penPoints = emptyList()
                                dragStart = null
                                dragEnd = null
                            },
                        )
                    },
            ) {
                if (bitmap != null && viewSize.width > 0) {
                    Canvas(Modifier.fillMaxSize()) {
                        val t = imageViewTransform(imageW, imageH, size.width, size.height, scale, panX, panY)
                        drawImage(
                            bitmap,
                            dstOffset = IntOffset(t.ox.toInt(), t.oy.toInt()),
                            dstSize = IntSize((imageW * t.fit).toInt(), (imageH * t.fit).toInt()),
                        )
                        fun toView(ix: Float, iy: Float): Offset {
                            val p = viewPoint(t, ix, iy)
                            return Offset(p.first, p.second)
                        }
                        val liveStroke = if (tool == MarkupTool.Pen && penPoints.size >= 2) {
                            MarkupStroke(penPoints, colorToArgb(color), 4f)
                        } else {
                            null
                        }
                        for (stroke in doc.strokes + listOfNotNull(liveStroke)) {
                            for (i in 1 until stroke.points.size) {
                                val a = stroke.points[i - 1]
                                val b = stroke.points[i]
                                drawLine(Color(stroke.color), toView(a.first, a.second), toView(b.first, b.second), strokeWidth = stroke.width)
                            }
                        }
                        for (arrow in doc.arrows) {
                            drawLine(
                                Color(arrow.color),
                                toView(arrow.x1, arrow.y1),
                                toView(arrow.x2, arrow.y2),
                                strokeWidth = arrow.width,
                            )
                        }
                        for (rect in doc.rects) {
                            val tl = toView(rect.left, rect.top)
                            val br = toView(rect.right, rect.bottom)
                            drawRect(
                                Color(rect.color),
                                topLeft = tl,
                                size = androidx.compose.ui.geometry.Size(br.x - tl.x, br.y - tl.y),
                                style = Stroke(rect.width),
                            )
                        }
                        doc.crop?.let { crop ->
                            val tl = toView(crop.left, crop.top)
                            val br = toView(crop.right, crop.bottom)
                            drawRect(
                                cropStroke,
                                topLeft = tl,
                                size = androidx.compose.ui.geometry.Size(br.x - tl.x, br.y - tl.y),
                                style = Stroke(2f),
                            )
                        }
                    }
                }
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 8.dp),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    MarkupToolButton(Icons.Pencil, "Pen", tool == MarkupTool.Pen) { tool = MarkupTool.Pen }
                    MarkupToolButton(Icons.Arrow, "Arrow", tool == MarkupTool.Arrow) { tool = MarkupTool.Arrow }
                    MarkupToolButton(Icons.Rect, "Box", tool == MarkupTool.Rectangle) { tool = MarkupTool.Rectangle }
                    MarkupToolButton(Icons.Text, "Text", tool == MarkupTool.Text) { tool = MarkupTool.Text }
                }
                Row(
                    Modifier
                        .padding(horizontal = 12.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    for ((_, swatch) in markupPalette(themeColors)) {
                        val picked = color == swatch
                        Box(
                            Modifier
                                .size(28.dp)
                                .border(
                                    if (picked) 2.dp else 0.5.dp,
                                    if (picked) Tokens.current.foreground else Tokens.current.line,
                                    CircleShape,
                                )
                                .padding(2.dp)
                                .clip(CircleShape)
                                .background(swatch)
                                .clickable { color = swatch },
                        )
                    }
                }
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircleButton(
                        Icons.Undo,
                        onClick = {
                            if (undo.isNotEmpty()) {
                                redo.add(0, doc)
                                doc = undo.removeAt(undo.lastIndex)
                            }
                        },
                        contentDescription = "Undo",
                        glass = false,
                    )
                    CircleButton(
                        Icons.Redo,
                        onClick = {
                            if (redo.isNotEmpty()) {
                                undo.add(doc)
                                doc = redo.removeAt(0)
                            }
                        },
                        contentDescription = "Redo",
                        glass = false,
                    )
                }
            }
        }
    }
    CenterDialog(
        title = "Label",
        visible = textOpen,
        onDismiss = { textOpen = false },
        field = textField,
        onFieldChange = { textField = it },
        buttons = listOf(
            DialogButtonSpec("Cancel") { textOpen = false },
            DialogButtonSpec("Add") {
                val at = textAt
                if (at != null && textField.isNotBlank()) {
                    pushDoc(
                        doc.copy(
                            labels = doc.labels + MarkupText(
                                at.first,
                                at.second,
                                textField.trim(),
                                colorToArgb(color),
                                32f,
                            ),
                        ),
                    )
                }
                textOpen = false
            },
        ),
    )
}

@Composable
private fun MarkupToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    CircleButton(
        icon,
        modifier = if (active) {
            Modifier.border(2.dp, Tokens.current.foreground, CircleShape)
        } else {
            Modifier
        },
        onClick = onClick,
        contentDescription = label,
        glass = false,
    )
}
