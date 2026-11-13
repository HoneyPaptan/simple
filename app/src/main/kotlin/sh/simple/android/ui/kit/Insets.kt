package sh.simple.android.ui.kit

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val ComposerFloatingClearance = 68.dp

@Composable
fun navigationBarBottomInset(): Dp =
    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

@Composable
fun listScrollBottomPadding(extra: Dp = 0.dp): PaddingValues {
    val bottom = navigationBarBottomInset() + extra
    return remember(bottom) { PaddingValues(bottom = bottom) }
}

@Composable
fun composerListScrollBottomPadding(): PaddingValues =
    listScrollBottomPadding(ComposerFloatingClearance)

@Composable
fun headerBarListTopPadding(): PaddingValues {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + HeaderContentHeight
    return remember(top) { PaddingValues(top = top) }
}

@Composable
fun composerListContentPadding(): PaddingValues {
    val top = headerBarListTopPadding().calculateTopPadding()
    val bottom = composerListScrollBottomPadding().calculateBottomPadding()
    return remember(top, bottom) { PaddingValues(top = top, bottom = bottom) }
}
