package sh.simple.android.ui.kit

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import sh.simple.android.ui.theme.Tokens

@Composable
fun AppText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = sh.simple.android.ui.theme.Type.body,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign? = null,
) {
    val ink = if (color == Color.Unspecified) Tokens.current.foreground else color
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(color = ink, textAlign = textAlign ?: style.textAlign),
        maxLines = maxLines,
        overflow = overflow,
    )
}
