package sh.simple.android.ui.agent

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.launch
import sh.simple.android.store.AgentEntity
import sh.simple.android.ui.kit.Motion
import sh.simple.android.ui.util.allAgentsOrder

data class AgentSwipeNeighbor(val hostId: String, val agentId: String)

@Stable
class AgentSwipeState(
    private val scope: kotlinx.coroutines.CoroutineScope,
) {
    var offsetPx by mutableFloatStateOf(0f)
        private set
    var contentAlpha by mutableFloatStateOf(1f)
        private set
    private val anim = Animatable(0f)
    var widthPx by mutableFloatStateOf(0f)
        private set

    fun onWidthMeasured(px: Float) {
        widthPx = px
    }

    fun onDrag(rawOffset: Float, hasPrev: Boolean, hasNext: Boolean) {
        val resisted = when {
            rawOffset > 0f && !hasPrev -> rawOffset * 0.35f
            rawOffset < 0f && !hasNext -> rawOffset * 0.35f
            else -> rawOffset
        }
        offsetPx = resisted
        val w = widthPx.coerceAtLeast(1f)
        contentAlpha = 1f - (abs(resisted) / w * 0.22f).coerceIn(0f, 0.22f)
    }

    fun resetDrag() {
        offsetPx = 0f
        contentAlpha = 1f
        scope.launch { anim.snapTo(0f) }
    }

    private fun syncFromAnim(value: Float) {
        offsetPx = value
        val w = widthPx.coerceAtLeast(1f)
        contentAlpha = 1f - (abs(value) / w * 0.22f).coerceIn(0f, 0.22f)
    }

    fun settleToNext(onCommit: (AgentSwipeNeighbor) -> Unit, neighbor: AgentSwipeNeighbor) {
        val w = widthPx.coerceAtLeast(1f)
        scope.launch {
            anim.snapTo(offsetPx)
            anim.animateTo(-w, Motion.layout()) {
                syncFromAnim(value)
            }
            onCommit(neighbor)
            offsetPx = 0f
            contentAlpha = 1f
            anim.snapTo(0f)
        }
    }

    fun settleToPrev(onCommit: (AgentSwipeNeighbor) -> Unit, neighbor: AgentSwipeNeighbor) {
        val w = widthPx.coerceAtLeast(1f)
        scope.launch {
            anim.snapTo(offsetPx)
            anim.animateTo(w, Motion.layout()) {
                syncFromAnim(value)
            }
            onCommit(neighbor)
            offsetPx = 0f
            contentAlpha = 1f
            anim.snapTo(0f)
        }
    }

    fun snapBack() {
        scope.launch {
            anim.snapTo(offsetPx)
            anim.animateTo(0f, Motion.layout()) {
                syncFromAnim(value)
            }
            offsetPx = 0f
            contentAlpha = 1f
            anim.snapTo(0f)
        }
    }
}

@Composable
fun rememberAgentSwipeState(): AgentSwipeState {
    val scope = rememberCoroutineScope()
    return remember { AgentSwipeState(scope) }
}

fun agentSwipeNeighbors(
    agents: List<AgentEntity>,
    hostId: String,
    agentId: String,
): Triple<Int, AgentSwipeNeighbor?, AgentSwipeNeighbor?> {
    val ordered = allAgentsOrder(agents)
    val index = ordered.indexOfFirst { it.hostId == hostId && it.id == agentId }
    if (index < 0) return Triple(-1, null, null)
    val prev = ordered.getOrNull(index - 1)?.let { AgentSwipeNeighbor(it.hostId, it.id) }
    val next = ordered.getOrNull(index + 1)?.let { AgentSwipeNeighbor(it.hostId, it.id) }
    return Triple(index, prev, next)
}

fun Modifier.agentSwipeMotion(state: AgentSwipeState): Modifier = this.then(
    Modifier.graphicsLayer {
        translationX = state.offsetPx
        alpha = state.contentAlpha
    },
)

fun Modifier.agentSwipeGesture(
    enabled: Boolean,
    state: AgentSwipeState,
    hasPrev: Boolean,
    hasNext: Boolean,
    onCommit: (AgentSwipeNeighbor) -> Unit,
    prev: AgentSwipeNeighbor?,
    next: AgentSwipeNeighbor?,
): Modifier = if (!enabled) this else composed {
    val edgePx = with(LocalDensity.current) { 24.dp.toPx() }
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val flingPx = with(LocalDensity.current) { 900.dp.toPx() }
    pointerInput(hasPrev, hasNext, prev, next, state.widthPx) {
        val threshold = (state.widthPx / 3f).coerceAtLeast(1f)
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (down.position.x < edgePx) return@awaitEachGesture
            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, down.position)
            var totalX = 0f
            var totalY = 0f
            var dragging = false
            while (true) {
                val event = awaitPointerEvent()
                val change: PointerInputChange = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
                    val vx = tracker.calculateVelocity().x
                    val goNext = totalX < 0f && hasNext && next != null &&
                        (totalX <= -threshold || vx <= -flingPx)
                    val goPrev = totalX > 0f && hasPrev && prev != null &&
                        (totalX >= threshold || vx >= flingPx)
                    when {
                        goNext -> state.settleToNext(onCommit, next)
                        goPrev -> state.settleToPrev(onCommit, prev)
                        else -> state.snapBack()
                    }
                    break
                }
                val delta = change.positionChange()
                if (!dragging) {
                    totalX += delta.x
                    totalY += delta.y
                    if (abs(totalX) > touchSlop || abs(totalY) > touchSlop) {
                        if (abs(totalX) <= abs(totalY)) break
                        dragging = true
                    }
                } else {
                    change.consume()
                    totalX += delta.x
                    tracker.addPosition(change.uptimeMillis, change.position)
                    state.onDrag(totalX, hasPrev, hasNext)
                }
            }
            if (!dragging) state.resetDrag()
        }
    }
}

@Composable
fun agentSwipeChromeHeight(): Dp = 68.dp
