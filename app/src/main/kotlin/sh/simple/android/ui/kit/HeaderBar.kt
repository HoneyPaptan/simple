package sh.simple.android.ui.kit

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.shapes.Capsule
import sh.simple.android.ui.glass.GlassKind
import sh.simple.android.ui.glass.ScrollEdge
import sh.simple.android.ui.glass.ScrollEdgeKind
import sh.simple.android.ui.glass.glass
import sh.simple.android.ui.glass.rememberPress
import sh.simple.android.ui.kit.pressScale
import sh.simple.android.ui.theme.Tokens
import sh.simple.android.ui.theme.Type

val HeaderContentHeight = 64.dp
val HeaderButtonSize = 44.dp
private val HeaderButtonGap = 6.dp
private val HeaderTitleGap = 6.dp
private val HeaderFadeExtra = 20.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HeaderBar(
    left: List<ImageVector>,
    title: String?,
    right: List<ImageVector>,
    subtitle: String? = null,
    subtitleContent: (@Composable () -> Unit)? = null,
    titleIcon: ImageVector? = null,
    titleIconDescription: String? = null,
    modifier: Modifier = Modifier,
    onLeftClick: (Int) -> Unit = {},
    onRightClick: (Int) -> Unit = {},
    onTitleLongClick: (() -> Unit)? = null,
    onTitleBounds: ((Rect) -> Unit)? = null,
    onRightBounds: ((Int, Rect) -> Unit)? = null,
) {
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val density = LocalDensity.current
    var titleHeightPx by remember { mutableIntStateOf(0) }
    val controlHeight = maxOf(HeaderButtonSize, with(density) { titleHeightPx.toDp() })
    Box(modifier.fillMaxWidth()) {
        ScrollEdge(ScrollEdgeKind.Top, statusTop + HeaderContentHeight + HeaderFadeExtra)
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(HeaderContentHeight)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HeaderButtonGap),
        ) {
            left.forEachIndexed { index, icon ->
                CircleButton(
                    icon,
                    onClick = { onLeftClick(index) },
                    contentDescription = iconActionLabel(icon),
                    size = controlHeight,
                )
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (title != null) {
                    HeaderTitlePill(
                        title = title,
                        subtitle = subtitle,
                        subtitleContent = subtitleContent,
                        titleIcon = titleIcon,
                        titleIconDescription = titleIconDescription,
                        onTitleLongClick = onTitleLongClick,
                        onTitleBounds = onTitleBounds,
                        onHeight = { titleHeightPx = it },
                    )
                }
            }
            if (right.isNotEmpty()) {
                HeaderButtonGroup(right, controlHeight, onRightClick, onRightBounds)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HeaderTitlePill(
    title: String,
    subtitle: String?,
    subtitleContent: (@Composable () -> Unit)?,
    titleIcon: ImageVector?,
    titleIconDescription: String?,
    onTitleLongClick: (() -> Unit)?,
    onTitleBounds: ((Rect) -> Unit)?,
    onHeight: (Int) -> Unit,
) {
    val shape = Capsule()
    Column(
        Modifier
            .then(
                if (onTitleBounds != null) {
                    Modifier.onGloballyPositioned { onTitleBounds(it.boundsInRoot()) }
                } else {
                    Modifier
                },
            )
            .onSizeChanged { onHeight(it.height) }
            .heightIn(min = HeaderButtonSize)
            .glass(shape, GlassKind.Pill)
            .clip(shape)
            .then(
                if (onTitleLongClick != null) {
                    Modifier.combinedClickable(
                        role = Role.Button,
                        onClick = {},
                        onLongClick = onTitleLongClick,
                    )
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 16.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HeaderTitleGap),
        ) {
            if (titleIcon != null) {
                SimpleIcon(
                    titleIcon,
                    size = 18.dp,
                    contentDescription = titleIconDescription ?: brandIconLabel(titleIcon),
                )
            }
            AppText(
                title,
                style = Type.headline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .semantics { heading() },
            )
        }
        when {
            subtitleContent != null -> subtitleContent()
            !subtitle.isNullOrEmpty() -> {
                AppText(
                    subtitle,
                    style = Type.caption,
                    color = Tokens.current.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun HeaderButtonGroup(
    icons: List<ImageVector>,
    height: Dp,
    onClick: (Int) -> Unit,
    onBounds: ((Int, Rect) -> Unit)?,
) {
    if (icons.size == 1) {
        CircleButton(
            icons[0],
            modifier = if (onBounds != null) {
                Modifier.onGloballyPositioned { onBounds(0, it.boundsInRoot()) }
            } else {
                Modifier
            },
            onClick = { onClick(0) },
            contentDescription = iconActionLabel(icons[0]),
            size = height,
        )
        return
    }
    val shape = Capsule()
    Row(
        Modifier
            .height(height)
            .glass(shape, GlassKind.Pill)
            .clip(shape)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icons.forEachIndexed { index, icon ->
            GroupIconButton(
                icon,
                modifier = if (onBounds != null) {
                    Modifier.onGloballyPositioned { onBounds(index, it.boundsInRoot()) }
                } else {
                    Modifier
                },
                height = height,
                onClick = { onClick(index) },
            )
        }
    }
}

@Composable
private fun GroupIconButton(icon: ImageVector, modifier: Modifier, height: Dp, onClick: () -> Unit) {
    val (source, pressed) = rememberPress()
    val shape = Capsule()
    Box(
        modifier
            .width(MinTouchTarget - 4.dp)
            .height(height)
            .pressScale(pressed)
            .clickable(source, null, role = Role.Button, onClick = onClick)
            .focusRing(shape),
        contentAlignment = Alignment.Center,
    ) {
        SimpleIcon(icon, size = 22.dp, contentDescription = iconActionLabel(icon))
    }
}

@Composable
fun CircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    contentDescription: String? = null,
    glass: Boolean = true,
    size: Dp = HeaderButtonSize,
) {
    val (source, pressed) = rememberPress()
    val shape = Capsule()
    val chrome = if (glass) {
        Modifier.glass(shape, GlassKind.Circle, pressed = pressed)
    } else {
        Modifier
            .pressScale(pressed)
            .clip(shape)
            .background(Tokens.current.card)
            .border(0.5.dp, Tokens.current.hairline, shape)
    }
    Box(
        modifier
            .minTouchTarget()
            .clickable(source, null, role = Role.Button, onClick = onClick)
            .focusRing(shape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(size)
                .then(chrome)
                .clip(shape),
            contentAlignment = Alignment.Center,
        ) {
            SimpleIcon(icon, size = 22.dp, contentDescription = contentDescription)
        }
    }
}

@Composable
fun LargeTitle(text: String, modifier: Modifier = Modifier) {
    AppText(
        text,
        modifier
            .fillMaxWidth()
            .padding(start = 20.dp, top = 12.dp)
            .semantics { heading() },
        style = Type.largeTitle,
    )
}
