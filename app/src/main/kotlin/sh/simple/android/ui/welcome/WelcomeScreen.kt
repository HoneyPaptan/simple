package sh.simple.android.ui.welcome

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.kyant.shapes.RoundedRectangle
import sh.simple.android.R
import sh.simple.android.INSTALL_DAEMON_CMD
import sh.simple.android.ui.kit.AppText
import sh.simple.android.ui.kit.Icons
import sh.simple.android.ui.kit.PrimaryButton
import sh.simple.android.ui.kit.SecondaryButton
import sh.simple.android.ui.kit.SimpleIcon
import sh.simple.android.ui.theme.LocalCodeStyle
import sh.simple.android.ui.theme.Tokens
import sh.simple.android.ui.theme.Type

private const val PAIR_CMD = "simpled pair"

@Composable
fun WelcomeScreen(
    onScan: () -> Unit,
    onEnterCode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val topGap = welcomeTopGap(LocalConfiguration.current.screenHeightDp)
    Column(
        modifier
            .fillMaxSize()
            .background(Tokens.current.brandSurface)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp)
            .padding(top = topGap.dp, bottom = 28.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(96.dp),
        )
        AppText(
            "Your coding agents,",
            Modifier.padding(top = 28.dp),
            style = Type.chatHeading,
        )
        AppText(
            "on your phone.",
            style = Type.chatHeading,
            color = Tokens.current.brandAccent,
        )
        AppText(
            "simple talks to your own computers over your tailnet. No cloud in between.",
            Modifier.padding(top = 14.dp),
            style = Type.body,
            color = Tokens.current.muted,
        )
        AppText("On your computer, run", Modifier.padding(top = 28.dp), style = Type.chip, color = Tokens.current.faint)
        CommandRow(INSTALL_DAEMON_CMD, context)
        CommandRow(PAIR_CMD, context)
        PrimaryButton("Scan pairing code", Modifier.padding(top = 28.dp), onClick = onScan)
        SecondaryButton("Enter code instead", Modifier.padding(top = 10.dp).fillMaxWidth().heightIn(min = 50.dp), onClick = onEnterCode)
    }
}

@Composable
private fun CommandRow(text: String, context: Context) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .border(1.dp, Tokens.current.line, RoundedRectangle(14.dp))
            .background(Tokens.current.card, RoundedRectangle(14.dp))
            .semantics { contentDescription = "Copy command" }
            .clickable(role = Role.Button) { copy(context, text) }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppText(text, Modifier.weight(1f), style = LocalCodeStyle.current)
        SimpleIcon(Icons.Copy, size = 16.dp, tint = Tokens.current.muted, contentDescription = null)
    }
}

internal fun welcomeTopGap(screenHeightDp: Int): Int = ((screenHeightDp - 640) / 2).coerceIn(24, 150)

private fun copy(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("command", text))
}
