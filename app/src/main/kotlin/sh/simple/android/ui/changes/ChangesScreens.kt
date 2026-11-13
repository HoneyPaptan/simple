package sh.simple.android.ui.changes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.simple.android.link.Hosts
import sh.simple.android.link.RemoteGateway
import sh.simple.android.link.gitCommitResultMessage
import sh.simple.android.link.gitRepoErrorMessage
import sh.simple.android.model.GitFileStatus
import sh.simple.android.model.GitStatusResult
import sh.simple.android.ui.glass.GlassStage
import sh.simple.android.ui.kit.AppText
import sh.simple.android.ui.kit.HeaderBar
import androidx.compose.foundation.shape.RoundedCornerShape
import sh.simple.android.ui.kit.Icons
import androidx.compose.foundation.text.BasicTextField
import sh.simple.android.ui.kit.GlassSheet
import sh.simple.android.ui.kit.PrimaryButton
import sh.simple.android.ui.kit.SimpleIcon
import sh.simple.android.ui.theme.LocalCodeStyle
import sh.simple.android.ui.theme.Tokens
import androidx.compose.ui.graphics.SolidColor
import sh.simple.android.ui.theme.Type
import sh.simple.android.ui.util.HostReconnectReloadEffect

private fun changesBranchSubtitle(snap: GitStatusResult): String = buildString {
    append(snap.branch)
    if (snap.upstream) {
        if (snap.ahead > 0) append(" · ${snap.ahead} ahead")
        if (snap.behind > 0) append(" · ${snap.behind} behind")
    }
}

@Composable
fun ChangesScreen(
    hosts: Hosts,
    hostId: String,
    cwd: String,
    onBack: () -> Unit,
    onOpenDiff: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var status by remember { mutableStateOf<GitStatusResult?>(null) }
    var error by remember { mutableStateOf("") }
    var commitOpen by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var push by remember { mutableStateOf(false) }
    var resultLine by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    fun reload() {
        scope.launch {
            error = ""
            status = withContext(Dispatchers.IO) {
                runCatching { RemoteGateway.gitStatus(hosts, hostId, cwd) }
                    .getOrElse {
                        error = gitRepoErrorMessage(it)
                        null
                    }
            }
        }
    }
    LaunchedEffect(hostId, cwd) { reload() }
    HostReconnectReloadEffect(hosts, hostId) { reload() }
    val snap = status
    val surface = if (snap?.files?.isEmpty() == true) Tokens.current.brandSurface else Tokens.current.background
    GlassStage(modifier.fillMaxSize().background(surface)) {
        Column(Modifier.fillMaxSize()) {
            HeaderBar(
                listOf(Icons.Back),
                "Changes",
                emptyList(),
                subtitle = snap?.let { changesBranchSubtitle(it) },
                onLeftClick = { onBack() },
            )
            if (error.isNotEmpty()) {
                AppText(error, Modifier.padding(20.dp), style = Type.meta, color = Tokens.current.muted)
            } else if (snap != null) {
                Column(Modifier.weight(1f).fillMaxWidth()) {
                    if (snap.files.isEmpty()) {
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                        ) {
                            AppText(
                                "No changes",
                                Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
                                style = Type.body,
                                color = Tokens.current.muted,
                            )
                        }
                    } else {
                        LazyColumn(Modifier.weight(1f)) {
                            items(snap.files, key = { it.path }) { row ->
                                ChangeRow(row) { onOpenDiff(row.path) }
                            }
                        }
                    }
                    if (resultLine.isNotEmpty()) {
                        AppText(
                            resultLine,
                            Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                            style = Type.meta,
                            color = Tokens.current.muted,
                            maxLines = 2,
                        )
                    }
                }
                PrimaryButton(
                    "Commit",
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    enabled = snap.files.isNotEmpty(),
                ) { commitOpen = true }
            }
        }
    }
    CommitSheet(
        open = commitOpen,
        onDismiss = { commitOpen = false },
        message = message,
        onMessageChange = { message = it },
        push = push,
        onPushChange = { push = it },
        onCommit = {
            val trimmed = message.trim()
            commitOpen = false
            scope.launch {
                resultLine = withContext(Dispatchers.IO) {
                    runCatching {
                        RemoteGateway.gitCommit(hosts, hostId, cwd, trimmed, push)
                        "Committed: $trimmed"
                    }.getOrElse { ex ->
                        gitCommitResultMessage("Committed: $trimmed.", ex)
                    }
                }
                message = ""
                reload()
            }
        },
    )
}

@Composable
private fun CommitSheet(
    open: Boolean,
    onDismiss: () -> Unit,
    message: String,
    onMessageChange: (String) -> Unit,
    push: Boolean,
    onPushChange: (Boolean) -> Unit,
    onCommit: () -> Unit,
) {
    GlassSheet(
        open,
        onDismiss,
        "Commit",
        stacked = true,
    ) {
        Column(Modifier.fillMaxWidth()) {
            BasicTextField(
                value = message,
                onValueChange = onMessageChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .background(Tokens.current.raised, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                textStyle = Type.body.copy(color = Tokens.current.foreground),
                cursorBrush = SolidColor(Tokens.current.foreground),
                decorationBox = { inner ->
                    if (message.isEmpty()) {
                        AppText("Commit message", style = Type.body, color = Tokens.current.muted)
                    }
                    inner()
                },
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onPushChange(!push) }
                    .padding(vertical = 4.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                AppText("Push", Modifier.weight(1f), style = Type.rowLabel)
                AppText(
                    if (push) "On" else "Off",
                    style = Type.meta,
                    color = Tokens.current.muted,
                    modifier = Modifier
                        .background(Tokens.current.raised, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 12.dp),
            ) {
                PrimaryButton("Commit", Modifier.fillMaxWidth(), enabled = message.trim().isNotEmpty(), onClick = onCommit)
            }
        }
    }
}

@Composable
private fun ChangeRow(row: GitFileStatus, onClick: () -> Unit) {
    val name = row.path.substringAfterLast('/')
    val dir = row.path.substringBeforeLast('/', "")
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        SimpleIcon(Icons.File, size = 19.dp, modifier = Modifier.padding(end = 12.dp))
        Column(Modifier.weight(1f)) {
            AppText(name, style = Type.rowLabel, maxLines = 1)
            if (dir.isNotEmpty()) {
                AppText(dir, style = Type.meta, color = Tokens.current.muted, maxLines = 1)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (row.status == "?") {
                AppText("New", style = Type.meta, color = Tokens.current.muted)
                if (row.add > 0) {
                    AppText("${row.add}", style = Type.meta, color = Tokens.current.muted)
                }
            } else {
                if (row.add > 0) {
                    AppText("+${row.add}", style = Type.meta, color = Tokens.current.add)
                }
                if (row.del > 0) {
                    AppText("−${row.del}", style = Type.meta, color = Tokens.current.remove)
                }
            }
            if (row.status != "?") {
                AppText(row.status, style = Type.meta, color = Tokens.current.muted)
            }
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 51.dp)
            .height(1.dp)
            .background(Tokens.current.hairline),
    )
}

@Composable
fun DiffScreen(
    hosts: Hosts,
    hostId: String,
    cwd: String,
    path: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var diff by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    fun reload() {
        scope.launch {
            error = ""
            diff = withContext(Dispatchers.IO) {
                runCatching { RemoteGateway.gitDiff(hosts, hostId, cwd, path).diff }
                    .getOrElse {
                        error = gitRepoErrorMessage(it)
                        ""
                    }
            }
        }
    }
    LaunchedEffect(hostId, cwd, path) { reload() }
    HostReconnectReloadEffect(hosts, hostId) { reload() }
    GlassStage(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            HeaderBar(listOf(Icons.Back), path.substringAfterLast('/'), emptyList(), onLeftClick = { onBack() })
            Column(
                Modifier
                    .weight(1f)
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                if (error.isNotEmpty()) {
                    AppText(error, style = Type.meta, color = Tokens.current.muted)
                } else {
                    parseDiffForDisplay(diff).forEach { row ->
                        DiffLineRow(row)
                    }
                }
            }
        }
    }
}

@Composable
private fun DiffLineRow(row: DiffDisplayLine) {
    val rowModifier = when (row.marker) {
        '+' -> Modifier.background(Tokens.current.diffAddBackground)
        '-' -> Modifier.background(Tokens.current.diffRemoveBackground)
        else -> Modifier
    }
    val markerChar = when (row.marker) {
        ' ' -> ' '
        '@' -> ' '
        else -> row.marker
    }
    val ink = if (row.marker == '@') Tokens.current.faint else Tokens.current.foreground
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 18.dp)
            .then(rowModifier),
    ) {
        AppText(
            markerChar.toString(),
            Modifier.width(16.dp),
            style = LocalCodeStyle.current,
            color = ink,
            maxLines = 1,
        )
        AppText(
            row.text,
            Modifier.weight(1f),
            style = LocalCodeStyle.current,
            color = ink,
            maxLines = 1,
        )
    }
}
