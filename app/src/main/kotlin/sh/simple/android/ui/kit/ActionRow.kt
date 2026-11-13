package sh.simple.android.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import sh.simple.android.ui.theme.Tokens
import sh.simple.android.ui.theme.Type

@Composable
fun ActionRow(
    title: String,
    subtitle: String? = null,
    subtitleContent: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (leading != null) {
                Box(Modifier.padding(end = 4.dp), contentAlignment = Alignment.Center) { leading() }
            }
            Column(Modifier.weight(1f)) {
                AppText(title, style = Type.rowLabel, maxLines = 2)
                when {
                    subtitleContent != null -> {
                        androidx.compose.foundation.layout.Box(Modifier.padding(top = 4.dp)) {
                            subtitleContent()
                        }
                    }
                    !subtitle.isNullOrEmpty() -> {
                        AppText(
                            subtitle,
                            Modifier.padding(top = 4.dp),
                            style = Type.meta,
                            color = Tokens.current.muted,
                            maxLines = 2,
                        )
                    }
                }
            }
            actions()
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp)
                .height(1.dp)
                .background(Tokens.current.hairline),
        )
    }
}
