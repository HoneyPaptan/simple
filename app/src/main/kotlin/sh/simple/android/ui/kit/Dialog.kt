package sh.simple.android.ui.kit

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import androidx.compose.foundation.border
import sh.simple.android.ui.glass.rememberPress
import sh.simple.android.ui.theme.Tokens
import sh.simple.android.ui.theme.Type
import sh.simple.android.ui.theme.medium

class DialogButtonSpec(val title: String, val destructive: Boolean = false, val action: () -> Unit)

@Composable
fun CenterDialog(
    title: String,
    visible: Boolean,
    onDismiss: () -> Unit,
    body: String = "",
    field: String = "",
    onFieldChange: ((String) -> Unit)? = null,
    buttons: List<DialogButtonSpec>,
) {
    val shown = remember { Animatable(0f) }
    var mounted by remember { mutableStateOf(visible) }
    LaunchedEffect(visible) {
        if (visible) {
            mounted = true
            shown.animateTo(1f, Motion.layout())
        } else {
            shown.animateTo(0f, Motion.fade())
            mounted = false
        }
    }
    if (!mounted) return
    val dismiss by rememberUpdatedState(onDismiss)
    val titleFocus = remember { FocusRequester() }
    LaunchedEffect(visible) {
        if (visible) runCatching { titleFocus.requestFocus() }
    }
    BackHandler(visible) { dismiss() }
    Overlay {
        Box(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .graphicsLayer { alpha = shown.value }
                .background(Tokens.current.dialogScrim)
                .clickable(remember { MutableInteractionSource() }, null, onClick = dismiss)
                .padding(horizontal = 44.dp),
            contentAlignment = Alignment.Center,
        ) {
            val shape = RoundedRectangle(30.dp)
            Column(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        val s = 0.96f + 0.04f * shown.value
                        scaleX = s
                        scaleY = s
                    }
                    .clip(shape)
                    .background(Tokens.current.card)
                    .border(1.dp, Tokens.current.line, shape)
                    .clickable(remember { MutableInteractionSource() }, null) {}
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        if (event.key == Key.Escape) {
                            dismiss()
                            true
                        } else {
                            false
                        }
                    }
                    .semantics { paneTitle = title }
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AppText(
                    title,
                    Modifier
                        .focusRequester(titleFocus)
                        .focusable()
                        .semantics { heading() },
                    style = Type.headline,
                    textAlign = TextAlign.Center,
                )
                if (body.isNotEmpty()) {
                    AppText(body, style = Type.meta, color = Tokens.current.muted, textAlign = TextAlign.Center)
                }
                if (onFieldChange != null) {
                    BasicTextField(
                        value = field,
                        onValueChange = onFieldChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(Capsule())
                            .background(Tokens.current.raised)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        textStyle = Type.body.copy(color = Tokens.current.foreground),
                        cursorBrush = SolidColor(Tokens.current.foreground),
                        singleLine = true,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (button in buttons) {
                        DialogPill(
                            button.title,
                            button.destructive,
                            Modifier.weight(1f),
                        ) {
                            dismiss()
                            button.action()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogPill(title: String, destructive: Boolean, modifier: Modifier, action: () -> Unit) {
    val (source, pressed) = rememberPress()
    Box(
        modifier
            .heightIn(min = 46.dp)
            .clip(Capsule())
            .background(if (destructive) Tokens.current.remove else Tokens.current.raised)
            .clickable(source, null, role = Role.Button, onClick = action)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        AppText(
            title,
            style = Type.rowLabel.medium,
            color = if (destructive) Tokens.current.foreground else Tokens.current.foreground,
            maxLines = 1,
        )
    }
}
