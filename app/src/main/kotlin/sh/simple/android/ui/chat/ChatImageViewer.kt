package sh.simple.android.ui.chat

import sh.simple.android.ui.util.rememberSampledImage
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.kyant.shapes.RoundedRectangle
import sh.simple.android.ui.kit.CircleButton
import sh.simple.android.ui.kit.Icons
import sh.simple.android.ui.theme.Tokens

@Composable
fun ChatImageThumbnail(path: String, onOpen: (String) -> Unit) {
    val thumb by rememberSampledImage(path, 480)
    val painter = thumb?.let { remember(it) { BitmapPainter(it) } }
    if (painter != null) {
        Image(
            painter,
            contentDescription = null,
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedRectangle(16.dp))
                .background(Tokens.current.raised)
                .semantics { contentDescription = "View image" }
                .clickable(role = Role.Button) { onOpen(path) },
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
fun ChatImageViewer(path: String, onDismiss: () -> Unit) {
    val full by rememberSampledImage(path, 4096)
    val painter = full?.let { remember(it) { BitmapPainter(it) } }
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }
    Box(
        Modifier
            .fillMaxSize()
            .background(Tokens.current.background.copy(alpha = 0.96f))
            .statusBarsPadding(),
    ) {
        CircleButton(Icons.X, Modifier.align(Alignment.TopEnd).padding(12.dp), onDismiss, contentDescription = "Close")
        if (painter != null) {
            Image(
                painter,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offsetX
                        translationY = offsetY + dragY
                    }
                    .pointerInput(path) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 6f)
                            offsetX += pan.x
                            offsetY += pan.y
                        }
                    }
                    .pointerInput(path) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                if (dragY > 120f) onDismiss()
                                dragY = 0f
                            },
                            onVerticalDrag = { _, delta -> dragY += delta },
                        )
                    },
                contentScale = ContentScale.Fit,
            )
        }
    }
}
