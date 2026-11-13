package sh.simple.android.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.simple.android.link.Hosts
import sh.simple.android.link.RemoteGateway
import sh.simple.android.link.rpcErrorMessage
import sh.simple.android.link.webhookTestErrorMessage
import sh.simple.android.model.WebhookView
import sh.simple.android.ui.glass.GlassStage
import sh.simple.android.ui.kit.AppText
import sh.simple.android.ui.kit.CenterDialog
import sh.simple.android.ui.kit.CircleButton
import sh.simple.android.ui.kit.DialogButtonSpec
import sh.simple.android.ui.kit.GlassSheet
import sh.simple.android.ui.kit.HeaderBar
import sh.simple.android.ui.kit.Icons
import sh.simple.android.ui.kit.PrimaryButton
import sh.simple.android.ui.kit.SecondaryButton
import sh.simple.android.ui.kit.listScrollBottomPadding
import sh.simple.android.ui.theme.Tokens
import sh.simple.android.ui.theme.Type
import sh.simple.android.ui.util.HostReconnectReloadEffect

private val webhookKinds = listOf("approval", "question", "prompt", "done", "failed")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WebhooksScreen(
    hosts: Hosts,
    hostId: String,
    hostName: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var rows by remember { mutableStateOf<List<WebhookView>>(emptyList()) }
    var loadError by remember { mutableStateOf("") }
    var addOpen by remember { mutableStateOf(false) }
    var addUrl by remember { mutableStateOf("") }
    var addKinds by remember { mutableStateOf(setOf("prompt")) }
    var addBusy by remember { mutableStateOf(false) }
    var addError by remember { mutableStateOf("") }
    var secretCard by remember { mutableStateOf("") }
    var removeTarget by remember { mutableStateOf<WebhookView?>(null) }
    val testStatus = remember { mutableStateMapOf<String, String>() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    fun refresh() {
        scope.launch {
            loadError = ""
            runCatching {
                withContext(Dispatchers.IO) { RemoteGateway.webhooksList(hosts, hostId) }
            }.onSuccess { rows = it.webhooks }.onFailure { loadError = rpcErrorMessage(it) }
        }
    }
    LaunchedEffect(hostId) { refresh() }
    HostReconnectReloadEffect(hosts, hostId) { refresh() }
    GlassSheet(
        visible = addOpen,
        onDismiss = {
            if (!addBusy) addOpen = false
        },
        title = "Add webhook",
        form = true,
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            AppText("URL", style = Type.meta, color = Tokens.current.muted)
            BasicTextField(
                value = addUrl,
                onValueChange = { addUrl = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 16.dp)
                    .clip(Capsule())
                    .background(Tokens.current.raised)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                textStyle = Type.body.copy(color = Tokens.current.foreground),
                cursorBrush = SolidColor(Tokens.current.foreground),
                singleLine = true,
            )
            AppText("Kinds", style = Type.meta, color = Tokens.current.muted)
            FlowRow(
                Modifier.padding(top = 8.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (kind in webhookKinds) {
                    val on = kind in addKinds
                    AppText(
                        kind,
                        Modifier
                            .clip(Capsule())
                            .background(if (on) Tokens.current.primaryFill else Tokens.current.raised)
                            .border(0.5.dp, Tokens.current.line, Capsule())
                            .clickable {
                                addKinds = if (on) addKinds - kind else addKinds + kind
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        style = Type.chip,
                        color = if (on) Tokens.current.onPrimary else Tokens.current.foreground,
                    )
                }
            }
            if (addError.isNotEmpty()) {
                AppText(addError, style = Type.meta, color = Tokens.current.remove)
            }
            PrimaryButton(if (addBusy) "Saving…" else "Save") {
                val url = addUrl.trim()
                if (url.isEmpty()) {
                    addError = "Enter a URL."
                    return@PrimaryButton
                }
                if (addKinds.isEmpty()) {
                    addError = "Pick at least one kind."
                    return@PrimaryButton
                }
                addBusy = true
                addError = ""
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            RemoteGateway.webhooksSet(hosts, hostId, url, addKinds.sorted())
                        }
                    }.onSuccess { res ->
                        addOpen = false
                        addUrl = ""
                        addKinds = setOf("prompt")
                        if (res.secret.isNotEmpty()) {
                            secretCard = res.secret
                        }
                        refresh()
                        addBusy = false
                    }.onFailure {
                        addError = rpcErrorMessage(it)
                        addBusy = false
                    }
                }
            }
        }
    }
    GlassStage(modifier.fillMaxSize().background(Tokens.current.background)) {
        Column(Modifier.fillMaxSize()) {
            HeaderBar(
                listOf(Icons.Back),
                "Webhooks",
                listOf(Icons.Plus),
                subtitle = hostName,
                onLeftClick = { _ -> onBack() },
                onRightClick = { addOpen = true },
            )
            if (loadError.isNotEmpty()) {
                AppText(loadError, Modifier.padding(20.dp), style = Type.body, color = Tokens.current.remove)
            }
            if (secretCard.isNotEmpty()) {
                Column(
                    Modifier
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .fillMaxWidth()
                        .clip(RoundedRectangle(16.dp))
                        .background(Tokens.current.card)
                        .padding(16.dp),
                ) {
                    AppText("Secret", style = Type.meta, color = Tokens.current.muted)
                    AppText(secretCard, Modifier.padding(vertical = 8.dp), style = Type.body)
                    AppText(
                        "Shown once. Use it to check X-Simple-Signature.",
                        style = Type.meta,
                        color = Tokens.current.muted,
                    )
                    SecondaryButton("Copy", Modifier.padding(top = 12.dp)) {
                        copyText(context, secretCard)
                    }
                }
            }
            if (rows.isEmpty() && loadError.isEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AppText("No webhooks yet.", style = Type.body, color = Tokens.current.muted)
                    SecondaryButton("Add webhook", Modifier.padding(top = 12.dp)) { addOpen = true }
                }
            } else {
                LazyColumn(contentPadding = listScrollBottomPadding()) {
                    items(rows, key = { it.id }) { row ->
                        WebhookRow(
                            row = row,
                            testLine = testStatus[row.id].orEmpty(),
                            onTest = {
                                scope.launch {
                                    testStatus[row.id] = ""
                                    runCatching {
                                        withContext(Dispatchers.IO) {
                                            RemoteGateway.webhooksTest(hosts, hostId, row.id)
                                        }
                                    }.onSuccess {
                                        testStatus[row.id] = "Test sent"
                                    }.onFailure {
                                        testStatus[row.id] = webhookTestErrorMessage(it)
                                    }
                                }
                            },
                            onRemove = { removeTarget = row },
                        )
                    }
                }
            }
        }
    }
    val remove = removeTarget
    if (remove != null) {
        CenterDialog(
            title = "Remove webhook?",
            visible = true,
            onDismiss = { removeTarget = null },
            body = remove.url,
            buttons = listOf(
                DialogButtonSpec("Cancel") { removeTarget = null },
                DialogButtonSpec("Remove", destructive = true) {
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                RemoteGateway.webhooksRemove(hosts, hostId, remove.id)
                            }
                        }.onSuccess {
                            removeTarget = null
                            testStatus.remove(remove.id)
                            refresh()
                        }.onFailure {
                            loadError = rpcErrorMessage(it)
                            removeTarget = null
                        }
                    }
                },
            ),
        )
    }
}

@Composable
private fun WebhookRow(
    row: WebhookView,
    testLine: String,
    onTest: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                AppText(row.url, style = Type.rowLabel, maxLines = 2)
                AppText(
                    row.kinds.joinToString(", "),
                    Modifier.padding(top = 4.dp),
                    style = Type.meta,
                    color = Tokens.current.muted,
                    maxLines = 2,
                )
                if (testLine.isNotEmpty()) {
                    AppText(
                        testLine,
                        Modifier.padding(top = 4.dp),
                        style = Type.meta,
                        color = if (testLine == "Sent") Tokens.current.muted else Tokens.current.remove,
                    )
                }
            }
            CircleButton(Icons.Bolt, glass = false, onClick = onTest, contentDescription = "Test webhook")
            CircleButton(Icons.Trash, glass = false, onClick = onRemove, contentDescription = "Remove webhook")
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(start = 51.dp)
                .height(1.dp)
                .background(Tokens.current.hairline),
        )
    }
}

private fun copyText(context: Context, text: String) {
    val cm = context.getSystemService(ClipboardManager::class.java) ?: return
    cm.setPrimaryClip(ClipData.newPlainText("webhook secret", text))
}
