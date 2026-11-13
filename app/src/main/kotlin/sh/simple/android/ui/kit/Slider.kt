package sh.simple.android.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kyant.shapes.Capsule
import kotlin.math.roundToInt
import sh.simple.android.ui.theme.Tokens
import sh.simple.android.ui.theme.Type

private fun Float.formatGlass(): String {
    val rounded = (this * 100f).roundToInt() / 100f
    return if (rounded == rounded.toLong().toFloat()) {
        rounded.toLong().toString()
    } else {
        "%.2f".format(rounded).trimEnd('0').trimEnd('.')
    }
}

@Composable
fun GlassFloatSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    format: (Float) -> String = { it.formatGlass() },
    infoWhat: String = "",
    infoCost: String = "",
) {
    val steps = ((range.endInclusive - range.start) * 100f).roundToInt().coerceAtLeast(1)
    GlassSlider(
        label = label,
        value = ((value - range.start) / (range.endInclusive - range.start) * steps).roundToInt(),
        range = 0..steps,
        onChange = { step ->
            onChange(range.start + (range.endInclusive - range.start) * step / steps)
        },
        modifier = modifier,
        enabled = enabled,
        suffix = "",
        valueLabel = format(value),
        infoWhat = infoWhat,
        infoCost = infoCost,
    )
}

@Composable
fun GlassSlider(
    label: String,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    suffix: String = "",
    enabled: Boolean = true,
    valueLabel: String? = null,
    infoWhat: String = "",
    infoCost: String = "",
) {
    val current by rememberUpdatedState(onChange)
    var width by remember { mutableIntStateOf(0) }
    val span = (range.last - range.first).coerceAtLeast(1)
    val fraction = (value - range.first).toFloat() / span
    val knob = with(LocalDensity.current) { 22.dp.toPx() }
    val ink = if (enabled) Tokens.current.foreground else Tokens.current.muted
    val track = if (enabled) Tokens.current.raised else Tokens.current.raised.copy(alpha = 0.5f)
    fun pick(x: Float) {
        if (!enabled) return
        val travel = (width - knob).coerceAtLeast(1f)
        current((range.first + ((x - knob / 2) / travel).coerceIn(0f, 1f) * span).roundToInt())
    }
    Column(
        modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(
                    value.toFloat(),
                    range.first.toFloat()..range.last.toFloat(),
                    (span - 1).coerceAtLeast(1),
                )
                setProgress { target -> current(target.roundToInt().coerceIn(range)); true }
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppText(label, style = Type.body, color = ink)
            if (infoWhat.isNotEmpty()) {
                SettingInfoTag(infoWhat, infoCost, Modifier.padding(start = 6.dp))
            }
            Spacer(Modifier.weight(1f))
            AppText(valueLabel ?: "$value$suffix", style = Type.meta, color = Tokens.current.muted)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(32.dp)
                .onSizeChanged { width = it.width }
                .pointerInput(range, enabled) {
                    if (enabled) detectTapGestures { pick(it.x) }
                }
                .pointerInput(range, enabled) {
                    if (enabled) {
                        detectHorizontalDragGestures(onDragStart = { pick(it.x) }) { change, _ ->
                            change.consume()
                            pick(change.position.x)
                        }
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(Modifier.fillMaxWidth().height(6.dp).clip(Capsule()).background(track))
            Box(
                Modifier
                    .width(with(LocalDensity.current) { (knob + (width - knob).coerceAtLeast(0f) * fraction).toDp() })
                    .height(6.dp)
                    .clip(Capsule())
                    .background(ink),
            )
            Box(
                Modifier
                    .offset { IntOffset(((width - knob).coerceAtLeast(0f) * fraction).roundToInt(), 0) }
                    .size(22.dp)
                    .shadow(2.dp, Capsule())
                    .clip(Capsule())
                    .background(Tokens.current.sliderHandle),
            )
        }
    }
}
