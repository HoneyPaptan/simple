package sh.simple.android.ui.kit

import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import sh.simple.android.ui.composer.ComposerVoicePhase
import sh.simple.android.ui.composer.voiceMicRingScale
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.unit.dp
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import sh.simple.android.ui.glass.GlassKind
import sh.simple.android.ui.glass.glass
import sh.simple.android.ui.glass.rememberPress
import sh.simple.android.ui.theme.Tokens
import sh.simple.android.ui.theme.Type

private val ComposerControlSize = 36.dp
private val ComposerIconSize = 20.dp
private const val VoiceLevelSmoothMs = 80

fun Modifier.composerChromeInset(): Modifier =
    navigationBarsPadding().padding(bottom = 12.dp)

@Composable
fun ComposerOverlay(
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .fillMaxSize()
            .background(Tokens.current.dialogScrim)
            .clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss)
            .navigationBarsPadding()
            .imePadding()
            .padding(bottom = 12.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            Modifier.pointerInput(Unit) { detectTapGestures {} },
            contentAlignment = Alignment.BottomCenter,
            content = content,
        )
    }
}

@Composable
fun ComposerPill(
    placeholder: String,
    onPlus: () -> Unit,
    onMic: () -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    plusModifier: Modifier = Modifier,
    trailingIcon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Mic,
    trailingIconDescription: String = iconActionLabel(Icons.Mic),
) {
    val (source, pressed) = rememberPress()
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .heightIn(min = 56.dp)
            .glass(Capsule(), GlassKind.Pill, pressed = pressed)
            .clip(Capsule())
            .clickable(source, null, role = Role.Button, onClick = onTap)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ComposerPlainCircle(Icons.Plus, onPlus, iconActionLabel(Icons.Plus), plusModifier)
        AppText(placeholder, Modifier.weight(1f), style = Type.body, color = Tokens.current.muted, maxLines = 2)
        ComposerPlainCircle(trailingIcon, onMic, trailingIconDescription)
    }
}

@Composable
fun ComposerCard(
    chips: @Composable () -> Unit,
    text: String,
    onTextChange: (String) -> Unit,
    modeLabel: String,
    modelLabel: String,
    sending: Boolean,
    onPlus: () -> Unit,
    onMic: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Plan, ask, build...",
    onModeClick: () -> Unit = {},
    onModelClick: () -> Unit = {},
    modeChipModifier: Modifier = Modifier,
) {
    var field by remember { mutableStateOf(TextFieldValue(text)) }
    ComposerCardFields(
        chips = chips,
        field = field,
        onFieldChange = {
            field = it
            onTextChange(it.text)
        },
        modeLabel = modeLabel,
        modelLabel = modelLabel,
        sending = sending,
        onPlus = onPlus,
        onMic = onMic,
        onSend = onSend,
        onStop = onStop,
        modifier = modifier,
        placeholder = placeholder,
        onModeClick = onModeClick,
        onModelClick = onModelClick,
        modeChipModifier = modeChipModifier,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ComposerCardFields(
    chips: @Composable () -> Unit,
    field: TextFieldValue,
    onFieldChange: (TextFieldValue) -> Unit,
    modeLabel: String,
    modelLabel: String,
    sending: Boolean,
    onPlus: () -> Unit,
    onMic: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Plan, ask, build...",
    onModeClick: () -> Unit = {},
    onModelClick: () -> Unit = {},
    modeChipModifier: Modifier = Modifier,
    voicePhase: ComposerVoicePhase = ComposerVoicePhase.Idle,
    voicePermissionHint: String = "",
    onVoiceCancel: () -> Unit = {},
    attachmentRow: @Composable () -> Unit = {},
    suggestionRow: @Composable () -> Unit = {},
    plusModifier: Modifier = Modifier,
    hardwareKeyboard: Boolean = false,
    onHardwareEnter: () -> Unit = {},
    onHardwareEscape: () -> Unit = {},
) {
    val cardShape = RoundedRectangle(28.dp)
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .glass(cardShape, GlassKind.Card)
            .clip(cardShape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        FlowRow(
            Modifier.padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            chips()
        }
        attachmentRow()
        if (voicePhase == ComposerVoicePhase.Transcribing) {
            AppText(
                "Transcribing...",
                Modifier.padding(top = 8.dp),
                style = Type.meta,
                color = Tokens.current.muted,
            )
        }
        if (voicePermissionHint.isNotEmpty()) {
            AppText(
                voicePermissionHint,
                Modifier.padding(top = 4.dp),
                style = Type.meta,
                color = Tokens.current.muted,
            )
        }
        suggestionRow()
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        val imeVisible = WindowInsets.isImeVisible
        val focusManager = LocalFocusManager.current
        var imeWasVisible by remember { mutableStateOf(false) }
        LaunchedEffect(imeVisible) {
            if (imeWasVisible && !imeVisible) focusManager.clearFocus()
            imeWasVisible = imeVisible
        }
        val fieldModifier = Modifier
            .fillMaxWidth()
            .focusRequester(focus)
            .padding(top = 8.dp)
            .heightIn(min = 23.dp, max = 23.dp * 6)
            .then(
                if (hardwareKeyboard) {
                    Modifier.onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.Enter -> if (event.isShiftPressed) {
                                false
                            } else {
                                onHardwareEnter()
                                true
                            }
                            Key.Escape -> {
                                onHardwareEscape()
                                true
                            }
                            else -> false
                        }
                    }
                } else {
                    Modifier
                },
            )
        BasicTextField(
            value = field,
            onValueChange = onFieldChange,
            modifier = fieldModifier,
            textStyle = Type.body.copy(color = Tokens.current.foreground),
            cursorBrush = SolidColor(Tokens.current.foreground),
            minLines = 1,
            maxLines = 6,
            decorationBox = { inner ->
                Box {
                    if (field.text.isEmpty()) {
                        AppText(placeholder, style = Type.body, color = Tokens.current.muted, maxLines = 1)
                    }
                    inner()
                }
            },
        )
        val recording = voicePhase is ComposerVoicePhase.Recording
        val recordLevel = (voicePhase as? ComposerVoicePhase.Recording)?.level ?: 0f
        val recordSec = (voicePhase as? ComposerVoicePhase.Recording)?.elapsedSec ?: 0
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ComposerPlainCircle(Icons.Plus, onPlus, iconActionLabel(Icons.Plus), plusModifier)
            Row(
                Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ComposerCompactChip(
                    modeLabel,
                    leadingIcon = Icons.Shield,
                    onClick = onModeClick,
                    modifier = modeChipModifier.widthIn(max = 140.dp),
                )
                ComposerCompactChip(
                    modelLabel,
                    onClick = onModelClick,
                    modifier = Modifier.weight(1f, fill = false),
                    contentDescription = "Model, $modelLabel",
                )
            }
            if (recording) {
                AppText(
                    "${recordSec}s",
                    style = Type.meta,
                    color = Tokens.current.muted,
                    maxLines = 1,
                )
                ComposerMicButton(
                    level = recordLevel,
                    onClick = onMic,
                    contentDescription = iconActionLabel(Icons.Mic),
                )
                ComposerPlainCircle(Icons.X, onVoiceCancel, iconActionLabel(Icons.X))
            } else {
                ComposerMicButton(
                    level = 0f,
                    onClick = onMic,
                    contentDescription = iconActionLabel(Icons.Mic),
                )
            }
            ComposerSendCircle(sending = sending, onSend = onSend, onStop = onStop)
        }
    }
}

@Composable
private fun ComposerCompactChip(
    label: String,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val (source, pressed) = rememberPress()
    Row(
        modifier
            .height(ComposerControlSize)
            .defaultMinSize(minWidth = 1.dp)
            .pressScale(pressed)
            .clip(Capsule())
            .background(Tokens.current.raised)
            .border(0.5.dp, Tokens.current.line, Capsule())
            .clickable(source, null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (leadingIcon != null) {
            SimpleIcon(leadingIcon, size = 14.dp, contentDescription = null)
        }
        AppText(
            label,
            style = Type.meta,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

@Composable
private fun ComposerMicButton(
    level: Float,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberReduceMotion()
    val targetScale = voiceMicRingScale(level, reduceMotion)
    val ringScale by animateFloatAsState(
        targetScale,
        tween(VoiceLevelSmoothMs),
        label = "ComposerMicLevel",
    )
    val (source, pressed) = rememberPress()
    val shape = Capsule()
    Box(
        modifier
            .minTouchTarget()
            .pressScale(pressed)
            .clickable(source, null, role = Role.Button, onClick = onClick)
            .focusRing(shape)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        if (level > 0f) {
            Box(
                Modifier
                    .size(ComposerControlSize)
                    .graphicsLayer { scaleX = ringScale; scaleY = ringScale }
                    .clip(shape)
                    .background(Tokens.current.foreground.copy(alpha = 0.14f)),
            )
        }
        Box(
            Modifier
                .size(ComposerControlSize)
                .clip(shape)
                .background(Tokens.current.raised)
                .border(0.5.dp, Tokens.current.line, shape),
            contentAlignment = Alignment.Center,
        ) {
            SimpleIcon(Icons.Mic, size = ComposerIconSize, contentDescription = null)
        }
    }
}

@Composable
private fun ComposerSendCircle(
    sending: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    val sendIcon = if (sending) Icons.Stop else Icons.Up
    val sendLabel = if (sending) iconActionLabel(Icons.Stop) else iconActionLabel(Icons.Up)
    val (source, pressed) = rememberPress()
    val shape = Capsule()
    Box(
        Modifier
            .minTouchTarget()
            .semantics(mergeDescendants = true) { contentDescription = sendLabel }
            .pressScale(pressed)
            .clip(shape)
            .clickable(
                source,
                null,
                role = Role.Button,
                onClick = if (sending) onStop else onSend,
            )
            .focusRing(shape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(ComposerControlSize)
                .clip(shape)
                .background(if (sending) Tokens.current.raised else Tokens.current.primaryFill)
                .then(
                    if (sending) Modifier.border(0.5.dp, Tokens.current.line, shape) else Modifier,
                ),
            contentAlignment = Alignment.Center,
        ) {
            SimpleIcon(
                sendIcon,
                size = ComposerIconSize,
                tint = if (sending) Tokens.current.foreground else Tokens.current.onPrimary,
                contentDescription = null,
            )
        }
    }
}

@Composable
fun GlassChip(
    label: String,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val (source, pressed) = rememberPress()
    Row(
        modifier
            .heightIn(min = if (onClick != null) MinTouchTarget else 30.dp)
            .pressScale(pressed)
            .clip(Capsule())
            .background(Tokens.current.raised)
            .border(0.5.dp, Tokens.current.line, Capsule())
            .then(if (onClick != null) Modifier.clickable(source, null, role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .then(modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (leadingIcon != null) {
            SimpleIcon(leadingIcon, size = 14.dp, contentDescription = null)
        }
        AppText(label, style = Type.chip, maxLines = 2)
        if (onClick != null) {
            SimpleIcon(Icons.ChevD, size = 14.dp, tint = Tokens.current.muted, contentDescription = null)
        }
    }
}

@Composable
private fun ComposerPlainCircle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val (source, pressed) = rememberPress()
    Box(
        modifier
            .minTouchTarget()
            .pressScale(pressed)
            .clip(Capsule())
            .clickable(source, null, role = Role.Button, onClick = onClick)
            .focusRing(Capsule()),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(ComposerControlSize)
                .clip(Capsule())
                .background(Tokens.current.raised)
                .border(0.5.dp, Tokens.current.line, Capsule()),
            contentAlignment = Alignment.Center,
        ) {
            SimpleIcon(
                icon,
                size = ComposerIconSize,
                contentDescription = contentDescription,
            )
        }
    }
}

@Composable
fun KitGalleryComposerSection() {
    var text by remember { mutableStateOf("") }
    ComposerCard(
        chips = {
            GlassChip("simple api", leadingIcon = Icons.Folder)
            GlassChip("main", leadingIcon = Icons.Branch)
            GlassChip("devbox", leadingIcon = Icons.Term)
        },
        text = text,
        onTextChange = { text = it },
        modeLabel = "Ask",
        modelLabel = "Opus 4.8",
        sending = false,
        onPlus = {},
        onMic = {},
        onSend = {},
        onStop = {},
        modifier = Modifier.padding(20.dp),
    )
}
