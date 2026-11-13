package sh.simple.android.ui.kit

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import sh.simple.android.ui.theme.Tokens
import sh.simple.android.ui.theme.Type
import sh.simple.android.ui.util.AgentStatusLine
import sh.simple.android.ui.util.agentKindIcon

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    AppText(
        text,
        modifier = modifier.padding(start = 20.dp, top = 22.dp, bottom = 6.dp),
        style = Type.section,
        color = Tokens.current.faint,
    )
}

@Composable
fun ListRow(
    icon: ImageVector,
    label: String,
    secondary: String = "",
    secondaryContent: (@Composable () -> Unit)? = null,
    trailing: String = "",
    chevron: Boolean = true,
    selected: Boolean = false,
    muted: Boolean = false,
    enabled: Boolean = true,
    secondaryOverflow: TextOverflow = TextOverflow.Ellipsis,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = {},
) {
    Column(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, enabled = enabled, onClick = onClick) else Modifier),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) {
                SimpleIcon(icon, size = 20.dp, tint = Tokens.current.foreground)
            }
            AppText(
                label,
                if (trailing.isNotEmpty()) Modifier.weight(1f) else Modifier,
                style = Type.rowLabel,
                color = if (muted || !enabled) Tokens.current.muted else Tokens.current.foreground,
                maxLines = 1,
                overflow = if (trailing.isNotEmpty()) TextOverflow.Ellipsis else TextOverflow.Clip,
            )
            when {
                trailing.isNotEmpty() -> {
                    AppText(
                        trailing,
                        Modifier.padding(start = 8.dp),
                        style = Type.meta,
                        color = Tokens.current.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End,
                    )
                }
                secondaryContent != null -> {
                    Box(
                        Modifier
                            .weight(1f)
                            .padding(start = 8.dp),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        secondaryContent()
                    }
                }
                secondary.isNotEmpty() -> {
                    AppText(
                        secondary,
                        Modifier
                            .weight(1f)
                            .padding(start = 8.dp),
                        style = Type.meta,
                        color = Tokens.current.muted,
                        maxLines = 1,
                        overflow = secondaryOverflow,
                        textAlign = TextAlign.End,
                    )
                }
                else -> Spacer(Modifier.weight(1f))
            }
            if (selected) {
                SimpleIcon(Icons.Check, size = 18.dp, tint = Tokens.current.foreground)
            } else if (chevron) {
                SimpleIcon(Icons.ChevR, size = 18.dp, tint = Tokens.current.muted)
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(start = 51.dp)
                .height(1.dp)
                .background(Tokens.current.hairline),
        )
    }
}

@Composable
fun AgentKindBadge(
    kind: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier.size(32.dp), contentAlignment = Alignment.Center) {
        SimpleIcon(agentKindIcon(kind), size = 24.dp)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AgentRow(
    kind: String,
    title: String,
    statusLine: AgentStatusLine,
    metaSuffix: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    titleBold: Boolean = false,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 75.dp)
            .combinedClickable(
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AgentKindBadge(kind, Modifier.padding(start = 6.dp, end = 10.dp))
        Column(Modifier.weight(1f)) {
            AppText(
                title,
                style = if (titleBold) Type.rowLabel.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) else Type.rowLabel,
                maxLines = 2,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                AgentStatusText(statusLine, Modifier.weight(1f, fill = false))
                if (metaSuffix.isNotEmpty()) {
                    AppText(
                        " · $metaSuffix",
                        style = Type.meta,
                        color = Tokens.current.muted,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
