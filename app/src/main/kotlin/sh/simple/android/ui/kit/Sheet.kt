package sh.simple.android.ui.kit

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kyant.shapes.Capsule
import com.kyant.shapes.UnevenRoundedRectangle
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import sh.simple.android.ui.glass.GlassKind
import sh.simple.android.ui.glass.glass
import sh.simple.android.ui.theme.Tokens
import sh.simple.android.ui.theme.Type
import sh.simple.android.ui.theme.semibold

enum class SheetDetent { Half, Full }

@Composable
fun GlassSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String,
    doneLabel: String? = null,
    onDone: (() -> Unit)? = null,
    stacked: Boolean = false,
    form: Boolean = false,
    content: @Composable () -> Unit,
) {
    val shown = remember { Animatable(0f) }
    var mounted by remember { mutableStateOf(visible) }
    LaunchedEffect(visible) {
        if (visible) {
            mounted = true
            shown.animateTo(1f, Motion.present())
        } else {
            shown.animateTo(0f, Motion.present())
            mounted = false
        }
    }
    if (!mounted) return
    val dismiss by rememberUpdatedState(onDismiss)
    BackHandler(visible) { dismiss() }
    Overlay {
        SheetFrame(shown.value, dismiss, title, doneLabel, onDone, stacked, form, content)
    }
}

@Composable
private fun SheetFrame(
    shown: Float,
    dismiss: () -> Unit,
    title: String,
    doneLabel: String?,
    onDone: (() -> Unit)?,
    stacked: Boolean,
    form: Boolean,
    content: @Composable () -> Unit,
) {
    var formPx by remember { mutableStateOf(0f) }
    var detent by remember { mutableStateOf(SheetDetent.Half) }
    val scope = rememberCoroutineScope()
    val drag = remember { Animatable(0f) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val fullPx = with(LocalDensity.current) { maxHeight.toPx() }
        val sheetPx = when {
            form -> if (formPx > 0f) formPx else fullPx * 0.5f
            detent == SheetDetent.Full -> fullPx
            else -> fullPx * 0.5f
        }
        val closeAt = sheetPx * 0.25f
        fun release(velocity: Float) {
            scope.launch {
                when {
                    drag.value > closeAt || velocity > 1600f -> dismiss()
                    drag.value < -60f || velocity < -1600f -> {
                        detent = SheetDetent.Full
                        drag.snapTo(0f)
                    }
                    else -> drag.animateTo(0f, Motion.layout())
                }
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Tokens.current.sheetScrim.copy(alpha = Tokens.current.sheetScrim.alpha * shown))
                .clickable(remember { MutableInteractionSource() }, null, onClick = dismiss),
        )
        val shape = UnevenRoundedRectangle(topStart = if (stacked) 40.dp else 36.dp, topEnd = if (stacked) 40.dp else 36.dp)
        val inset = if (stacked) 9.dp else 0.dp
        val offsetY = (1f - shown) * sheetPx + drag.value
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    if (event.key == Key.Escape) {
                        dismiss()
                        true
                    } else {
                        false
                    }
                }
                .padding(horizontal = inset)
                .fillMaxWidth()
                .then(
                    if (form) {
                        Modifier
                            .heightIn(max = maxHeight - WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                            .wrapContentHeight()
                            .onSizeChanged { formPx = it.height.toFloat() }
                    } else {
                        Modifier.height(with(LocalDensity.current) { sheetPx.toDp() })
                    },
                )
                .offset { IntOffset(0, offsetY.roundToInt()) }
                .graphicsLayer {
                    shadowElevation = 24f
                    clip = true
                    this.shape = shape
                }
                .glass(shape, GlassKind.Sheet)
                .clip(shape)
                .navigationBarsPadding()
                .then(if (form) Modifier.imePadding() else Modifier)
                .draggable(
                    rememberDraggableState { delta ->
                        scope.launch { drag.snapTo((drag.value + delta).coerceAtLeast(if (detent == SheetDetent.Full) 0f else -120f)) }
                    },
                    Orientation.Vertical,
                    onDragStopped = { release(it) },
                )
                .clickable(remember { MutableInteractionSource() }, null) {},
        ) {
            Column(if (form) Modifier.fillMaxWidth() else Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().padding(top = 5.dp), contentAlignment = Alignment.TopCenter) {
                        Box(
                            Modifier
                                .size(width = 36.dp, height = 5.dp)
                                .clip(Capsule())
                                .background(Tokens.current.muted.copy(alpha = 0.45f)),
                        )
                    }
                    SheetHeader(
                        title = title,
                        doneLabel = doneLabel?.takeIf { onDone != null },
                        onDone = onDone ?: dismiss,
                        onClose = dismiss,
                        modifier = if (detent == SheetDetent.Full) Modifier.statusBarsPadding() else Modifier,
                    )
                if (form) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 12.dp),
                    ) { content() }
                } else {
                    Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)) { content() }
                }
            }
        }
    }
}

@Composable
private fun SheetHeader(
    title: String,
    doneLabel: String?,
    onDone: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val titleFocus = remember { FocusRequester() }
    LaunchedEffect(title) { runCatching { titleFocus.requestFocus() } }
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .heightIn(min = 44.dp),
    ) {
        if (doneLabel == null) {
            CircleButton(Icons.X, Modifier.align(Alignment.CenterStart), onClick = onClose, contentDescription = "Close", glass = false)
        }
        AppText(
            title,
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 88.dp)
                .focusRequester(titleFocus)
                .focusable()
                .semantics { heading() },
            style = Type.headline.semibold,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (doneLabel != null) {
            SecondaryButton(doneLabel, onClick = onDone, modifier = Modifier.align(Alignment.CenterEnd))
        }
    }
}
