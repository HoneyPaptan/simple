package sh.simple.android.ui.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

const val UPLOAD_MAX_EDGE = 2048
private const val UPLOAD_QUALITY = 85
private const val UPLOAD_KEEP_BYTES = 400_000L

fun sampleSizeFor(width: Int, height: Int, maxEdge: Int): Int {
    var sample = 1
    while (maxOf(width, height) / (sample * 2) >= maxEdge) sample *= 2
    return sample
}

fun decodeSampled(path: String, maxEdge: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val opts = BitmapFactory.Options().apply { inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxEdge) }
    val raw = BitmapFactory.decodeFile(path, opts) ?: return null
    val scaled = scaleDown(raw, maxEdge)
    return rotateByExif(path, scaled)
}

private fun scaleDown(src: Bitmap, maxEdge: Int): Bitmap {
    val edge = maxOf(src.width, src.height)
    if (edge <= maxEdge) return src
    val f = maxEdge.toFloat() / edge
    return Bitmap.createScaledBitmap(src, (src.width * f).toInt().coerceAtLeast(1), (src.height * f).toInt().coerceAtLeast(1), true)
}

private fun rotateByExif(path: String, src: Bitmap): Bitmap {
    val degrees = runCatching {
        when (ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    }.getOrDefault(0f)
    if (degrees == 0f) return src
    val m = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
}

data class UploadImage(val bytes: ByteArray, val mime: String)

fun compactForUpload(path: String, mime: String): UploadImage {
    val file = File(path)
    if (mime == "image/gif") return UploadImage(file.readBytes(), mime)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    val small = maxOf(bounds.outWidth, bounds.outHeight) in 1..UPLOAD_MAX_EDGE
    if (small && file.length() <= UPLOAD_KEEP_BYTES) return UploadImage(file.readBytes(), mime)
    val bmp = decodeSampled(path, UPLOAD_MAX_EDGE) ?: return UploadImage(file.readBytes(), mime)
    val out = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.JPEG, UPLOAD_QUALITY, out)
    return UploadImage(out.toByteArray(), "image/jpeg")
}

@Composable
fun rememberSampledImage(path: String, maxEdge: Int): State<ImageBitmap?> =
    produceState<ImageBitmap?>(null, path, maxEdge) {
        value = withContext(Dispatchers.IO) { decodeSampled(path, maxEdge)?.asImageBitmap() }
    }
