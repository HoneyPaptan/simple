package sh.simple.android.ui.inbox

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import sh.simple.android.model.Item
import sh.simple.android.model.promptStyle
import sh.simple.android.ui.kit.ActionCard
import sh.simple.android.ui.kit.ChoiceCard
import sh.simple.android.ui.kit.ContinueCard
import sh.simple.android.ui.kit.ErrorCard
import sh.simple.android.ui.kit.InputCard
import sh.simple.android.ui.kit.LinkCard
import sh.simple.android.ui.kit.PlanCard
import sh.simple.android.ui.kit.SentCard
import sh.simple.android.ui.kit.YesNoCard
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import sh.simple.android.ui.kit.AppText
import sh.simple.android.ui.theme.Tokens
import sh.simple.android.ui.theme.Type

val needsYouKinds = setOf("approval", "question", "plan", "prompt")

@Composable
fun InboxItemCard(
    item: Item,
    sent: Boolean,
    modifier: Modifier = Modifier,
    error: String = "",
    onAnswer: (decision: String, option: String?, text: String?) -> Unit,
) {
    val body = item.body.orEmpty()
    if (sent) {
        SentCard(item.title, body, modifier)
        return
    }
    Column(modifier) {
        when (item.kind) {
        "approval" -> ActionCard(
            item.title,
            approvalCardSubtitle(item),
            approvalCardCommand(item),
            "Allow",
            "Deny",
            onPrimary = { onAnswer("allow", null, null) },
            onSecondary = { onAnswer("deny", null, null) },
            link = "Always allow",
            onLink = { onAnswer("always", null, null) },
        )
        "question" -> ChoiceCard(
            item.title,
            body,
            item.options.map { it.label },
            onPick = { index ->
                val opt = item.options.getOrNull(index)?.id
                onAnswer("allow", opt, null)
            },
        )
        "plan" -> PlanCard(
            item.title,
            body,
            onApprove = { onAnswer("allow", null, null) },
            onKeepPlanning = { onAnswer("deny", null, null) },
        )
        "prompt" -> PromptCard(item, Modifier, onAnswer)
        }
        if (error.isNotEmpty()) {
            AppText(
                error,
                Modifier.padding(top = 8.dp),
                style = Type.meta,
                color = Tokens.current.attention,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun PromptCard(
    item: Item,
    modifier: Modifier,
    onAnswer: (decision: String, option: String?, text: String?) -> Unit,
) {
    val body = item.body.orEmpty()
    when (item.promptStyle) {
        "choice" -> ChoiceCard(
            item.title,
            body,
            item.options.map { it.label },
            onPick = { index ->
                val opt = item.options.getOrNull(index)?.id
                onAnswer("allow", opt, null)
            },
            modifier = modifier,
        )
        "yesno" -> YesNoCard(
            item.title,
            body,
            onYes = { onAnswer("allow", null, null) },
            onNo = { onAnswer("deny", null, null) },
            modifier = modifier,
        )
        "continue" -> ContinueCard(item.title, body, onContinue = { onAnswer("allow", null, null) }, modifier)
        "link" -> {
            val context = LocalContext.current
            val url = item.link.orEmpty()
            LinkCard(item.title, body, url, onOpen = {
                if (url.isNotBlank()) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
                onAnswer("allow", null, null)
            }, modifier)
        }
        "input" -> {
            var text by remember(item.id) { mutableStateOf("") }
            InputCard(
                item.title,
                body,
                text,
                onValueChange = { text = it },
                onSend = { onAnswer("allow", null, text) },
                modifier = modifier,
            )
        }
        "error" -> ErrorCard(item.title, body, onRetry = { onAnswer("allow", null, null) }, modifier)
        else -> ChoiceCard(item.title, body, listOf("OK"), onPick = { onAnswer("allow", null, null) }, modifier)
    }
}
