package sh.simple.android.ui.pane

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import sh.simple.android.model.Pane
import sh.simple.android.ui.kit.AppText
import sh.simple.android.ui.theme.Tokens
import sh.simple.android.ui.theme.Type

@Composable
fun PaneSwitcherRow(
    panes: List<Pane>,
    currentId: String,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (panes.size < 2) return
    val colors = Tokens.current
    val listState = rememberLazyListState()
    val currentIndex = panes.indexOfFirst { it.id == currentId }
    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0) listState.animateScrollToItem(currentIndex)
    }
    LazyRow(
        modifier.fillMaxWidth(),
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(panes, key = { it.id }) { pane ->
            val picked = pane.id == currentId
            AppText(
                pane.title,
                Modifier
                    .widthIn(max = 200.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (picked) colors.raised else colors.card)
                    .border(0.5.dp, if (picked) colors.foreground else colors.hairline, RoundedCornerShape(16.dp))
                    .selectable(selected = picked, role = Role.Tab) { onPick(pane.id) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                style = Type.chip,
                color = if (picked) colors.foreground else colors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

fun paneSwipeNeighbors(
    panes: List<Pane>,
    currentId: String,
): Triple<Int, Pane?, Pane?> {
    val index = panes.indexOfFirst { it.id == currentId }
    if (index < 0) return Triple(-1, null, null)
    val prev = panes.getOrNull(index - 1)
    val next = panes.getOrNull(index + 1)
    return Triple(index, prev, next)
}
