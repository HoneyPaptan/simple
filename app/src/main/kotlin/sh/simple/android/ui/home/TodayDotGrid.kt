package sh.simple.android.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import sh.simple.android.ui.theme.Tokens

@Composable
fun Modifier.todayDotGrid(): Modifier {
    val color = Tokens.current.hairline
    return drawBehind {
    val dotRadius = 1.5.dp.toPx()
    val step = 24.dp.toPx()
    var y = step / 2f
    while (y < size.height) {
        var x = step / 2f
        while (x < size.width) {
            drawCircle(color, dotRadius, Offset(x, y))
            x += step
        }
        y += step
    }
    }
}
