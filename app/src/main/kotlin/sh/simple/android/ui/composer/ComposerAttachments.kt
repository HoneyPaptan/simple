package sh.simple.android.ui.composer

import sh.simple.android.ui.util.rememberSampledImage
import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.compose.ui.graphics.painter.BitmapPainter
import com.kyant.shapes.RoundedRectangle
import sh.simple.android.ui.kit.Icons
import sh.simple.android.ui.kit.SimpleIcon
import sh.simple.android.ui.kit.minTouchTarget
import sh.simple.android.ui.theme.Tokens

class ComposerAttachmentState {
    val attachments = mutableStateListOf<ComposerAttachment>()

    fun add(item: ComposerAttachment) {
        attachments.add(item)
    }

    fun remove(id: String) {
        attachments.removeAll { it.id == id }
    }

    fun markMarkup(id: String) {
        val i = attachments.indexOfFirst { it.id == id }
        if (i >= 0) {
            val old = attachments[i]
            attachments[i] = old.copy(markup = true)
        }
    }

    fun clear() {
        attachments.clear()
    }
}

@Composable
fun rememberComposerAttachmentState(): ComposerAttachmentState = remember { ComposerAttachmentState() }

class ComposerAttachmentPickersHandle(
    val openPhoto: () -> Unit,
    val openCamera: () -> Unit,
)

@Composable
fun rememberComposerAttachmentPickers(state: ComposerAttachmentState): ComposerAttachmentPickersHandle {
    val context = LocalContext.current
    var pendingCameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        importAttachment(context, uri)?.let { state.add(it) }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val path = pendingCameraPath
        pendingCameraPath = null
        if (!ok || path == null) return@rememberLauncherForActivityResult
        importAttachment(context, Uri.fromFile(java.io.File(path)), "image/jpeg")?.let { state.add(it) }
    }
    val launchCamera = {
        val file = newCameraCaptureFile(context)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        pendingCameraPath = file.absolutePath
        try {
            cameraLauncher.launch(uri)
        } catch (_: ActivityNotFoundException) {
            pendingCameraPath = null
        }
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera()
    }
    return remember(state) {
        ComposerAttachmentPickersHandle(
            openPhoto = { photoPicker.launch("image/*") },
            openCamera = {
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
                if (granted) launchCamera() else cameraPermission.launch(Manifest.permission.CAMERA)
            },
        )
    }
}

@Composable
fun ComposerAttachmentRow(
    state: ComposerAttachmentState,
    onRemove: (String) -> Unit,
    onOpen: (ComposerAttachment) -> Unit = {},
) {
    if (state.attachments.isEmpty()) return
    Row(
        Modifier.padding(top = 8.dp, end = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        for (item in state.attachments) {
            val thumb by rememberSampledImage(item.localPath, 256)
            val painter = thumb?.let { remember(it) { BitmapPainter(it) } }
            Box(Modifier.size(56.dp)) {
                if (painter != null) {
                    Image(
                        painter,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedRectangle(12.dp))
                            .background(Tokens.current.raised)
                            .semantics { contentDescription = "Open attachment" }
                            .clickable(role = Role.Button) { onOpen(item) },
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clip(RoundedRectangle(12.dp))
                            .background(Tokens.current.raised),
                    )
                }
                if (item.markup) {
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(4.dp)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(Tokens.current.primaryFill)
                            .border(1.dp, Tokens.current.line, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        SimpleIcon(Icons.Pencil, size = 10.dp, tint = Tokens.current.onPrimary, contentDescription = null)
                    }
                }
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 12.dp, y = (-12).dp)
                        .minTouchTarget()
                        .semantics { contentDescription = "Remove attachment" }
                        .clickable(role = Role.Button) { onRemove(item.id) },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Tokens.current.card)
                            .border(1.dp, Tokens.current.line, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        SimpleIcon(Icons.X, size = 12.dp, contentDescription = null)
                    }
                }
            }
        }
    }
}
