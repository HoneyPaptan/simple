package sh.simple.android.ui.composer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.graphics.RectF
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import java.io.FileOutputStream
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

enum class MarkupTool { Pen, Arrow, Rectangle, Text }

data class MarkupStroke(val points: List<Pair<Float, Float>>, val color: Int, val width: Float)

data class MarkupArrow(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val color: Int, val width: Float)

data class MarkupRect(val left: Float, val top: Float, val right: Float, val bottom: Float, val color: Int, val width: Float)

data class MarkupText(val x: Float, val y: Float, val text: String, val color: Int, val size: Float)

data class MarkupCrop(val left: Float, val top: Float, val right: Float, val bottom: Float)

data class MarkupDocument(
    val strokes: List<MarkupStroke> = emptyList(),
    val arrows: List<MarkupArrow> = emptyList(),
    val rects: List<MarkupRect> = emptyList(),
    val labels: List<MarkupText> = emptyList(),
    val crop: MarkupCrop? = null,
)

fun markupPalette(colors: sh.simple.android.ui.theme.SimpleColors): List<Pair<String, Color>> = listOf(
    "Red" to colors.remove,
    "Blue" to colors.brandAccent,
    "Green" to colors.add,
    "White" to colors.onPrimary,
    "Black" to colors.foreground,
)

fun flattenMarkupToPng(sourcePath: String, doc: MarkupDocument): Boolean {
    val src = BitmapFactory.decodeFile(sourcePath) ?: return false
    val crop = doc.crop
    val base = if (crop != null) {
        val l = crop.left.coerceIn(0f, src.width.toFloat())
        val t = crop.top.coerceIn(0f, src.height.toFloat())
        val r = crop.right.coerceIn(l, src.width.toFloat())
        val b = crop.bottom.coerceIn(t, src.height.toFloat())
        Bitmap.createBitmap(src, l.toInt(), t.toInt(), (r - l).toInt().coerceAtLeast(1), (b - t).toInt().coerceAtLeast(1))
    } else {
        src.copy(Bitmap.Config.ARGB_8888, true)
    }
    val canvas = AndroidCanvas(base)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    for (stroke in doc.strokes) {
        if (stroke.points.size < 2) continue
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = stroke.width
        paint.color = stroke.color
        val path = AndroidPath()
        path.moveTo(stroke.points.first().first, stroke.points.first().second)
        for (i in 1 until stroke.points.size) {
            path.lineTo(stroke.points[i].first, stroke.points[i].second)
        }
        canvas.drawPath(path, paint)
    }
    for (arrow in doc.arrows) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = arrow.width
        paint.color = arrow.color
        canvas.drawLine(arrow.x1, arrow.y1, arrow.x2, arrow.y2, paint)
        val angle = atan2((arrow.y2 - arrow.y1).toDouble(), (arrow.x2 - arrow.x1).toDouble())
        val head = 18f
        val x2 = arrow.x2
        val y2 = arrow.y2
        canvas.drawLine(
            x2,
            y2,
            (x2 - head * cos(angle - 0.5)).toFloat(),
            (y2 - head * sin(angle - 0.5)).toFloat(),
            paint,
        )
        canvas.drawLine(
            x2,
            y2,
            (x2 - head * cos(angle + 0.5)).toFloat(),
            (y2 - head * sin(angle + 0.5)).toFloat(),
            paint,
        )
    }
    for (rect in doc.rects) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = rect.width
        paint.color = rect.color
        canvas.drawRect(RectF(rect.left, rect.top, rect.right, rect.bottom), paint)
    }
    for (label in doc.labels) {
        paint.style = Paint.Style.FILL
        paint.color = label.color
        paint.textSize = label.size
        canvas.drawText(label.text, label.x, label.y, paint)
    }
    FileOutputStream(sourcePath).use { out ->
        base.compress(Bitmap.CompressFormat.PNG, 100, out)
    }
    if (base !== src) base.recycle()
    src.recycle()
    return true
}

fun colorToArgb(color: Color): Int = color.toArgb()

fun imageSize(path: String): Pair<Int, Int> {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, opts)
    return opts.outWidth.coerceAtLeast(1) to opts.outHeight.coerceAtLeast(1)
}

fun imageViewTransform(imageW: Int, imageH: Int, viewW: Float, viewH: Float, scale: Float, panX: Float, panY: Float): ImageViewTransform {
    val fit = minOf(viewW / imageW, viewH / imageH) * scale
    val drawW = imageW * fit
    val drawH = imageH * fit
    val ox = (viewW - drawW) / 2f + panX
    val oy = (viewH - drawH) / 2f + panY
    return ImageViewTransform(fit, ox, oy)
}

data class ImageViewTransform(val fit: Float, val ox: Float, val oy: Float)

fun mapPoint(imageW: Int, imageH: Int, viewW: Float, viewH: Float, x: Float, y: Float, scale: Float, panX: Float, panY: Float): Pair<Float, Float> {
    val t = imageViewTransform(imageW, imageH, viewW, viewH, scale, panX, panY)
    val ix = ((x - t.ox) / t.fit).coerceIn(0f, imageW.toFloat())
    val iy = ((y - t.oy) / t.fit).coerceIn(0f, imageH.toFloat())
    return ix to iy
}

fun viewPoint(t: ImageViewTransform, ix: Float, iy: Float): Pair<Float, Float> =
    (t.ox + ix * t.fit) to (t.oy + iy * t.fit)
