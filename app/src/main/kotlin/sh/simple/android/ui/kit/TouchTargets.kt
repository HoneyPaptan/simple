package sh.simple.android.ui.kit

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val MinTouchTarget = 48.dp

fun Modifier.minTouchTarget(size: Dp = MinTouchTarget): Modifier = this.then(Modifier.size(size))
