package sh.simple.android.ui.servers

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import sh.simple.android.link.rpcErrorMessage
import sh.simple.android.model.ServerEntry
import sh.simple.android.ui.glass.GlassStage
import sh.simple.android.ui.kit.ActionRow
import sh.simple.android.ui.kit.AppText
import sh.simple.android.ui.kit.CenterDialog
import sh.simple.android.ui.kit.CircleButton
import sh.simple.android.ui.kit.DialogButtonSpec
import sh.simple.android.ui.kit.EmptyState
import sh.simple.android.ui.kit.ErrorState
import sh.simple.android.ui.kit.HeaderBar
import sh.simple.android.ui.kit.LoadingRow
import sh.simple.android.ui.kit.Icons
import sh.simple.android.ui.kit.listScrollBottomPadding
import sh.simple.android.ui.nav.Screen
import sh.simple.android.ui.theme.Tokens
import sh.simple.android.ui.theme.Type
import sh.simple.android.ui.util.HostReconnectReloadEffect

@Composable
fun ServersScreen(
    hosts: Hosts,
    hostId: String,
    title: String,
    onBack: () -> Unit,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier,
) {
    var rows by remember { mutableStateOf<List<ServerEntry>>(emptyList()) }
    var error by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var stopTarget by remember { mutableStateOf<ServerEntry?>(null) }
    val scope = rememberCoroutineScope()
    fun refresh() {
        scope.launch {
            loading = true
            error = ""
            runCatching {
                withContext(Dispatchers.IO) { RemoteGateway.serversList(hosts, hostId) }
            }.onSuccess {
                rows = it.servers
                loading = false
            }.onFailure {
                error = rpcErrorMessage(it)
                loading = false
            }
        }
    }
    LaunchedEffect(hostId) { refresh() }
    HostReconnectReloadEffect(hosts, hostId) {
        if (error.isNotEmpty()) refresh()
    }
    GlassStage(modifier.fillMaxSize().background(Tokens.current.background)) {
        Column(Modifier.fillMaxSize()) {
            HeaderBar(listOf(Icons.Back), "Servers", emptyList(), subtitle = title, onLeftClick = { _ -> onBack() })
            when {
                loading -> LoadingRow()
                error.isNotEmpty() -> ErrorState(error, onRetry = { refresh() })
                rows.isEmpty() -> EmptyState(
                    Icons.Server,
                    "No servers running",
                    "Dev servers listening on this computer show up here.",
                )
                else -> LazyColumn(
                    Modifier.weight(1f),
                    contentPadding = listScrollBottomPadding(),
                ) {
                    items(rows, key = { "${it.port}:${it.pid}" }) { row ->
                        ServerRow(
                            row = row,
                            onOpen = {
                                scope.launch {
                                    runCatching {
                                        withContext(Dispatchers.IO) {
                                            RemoteGateway.previewOpen(hosts, hostId, row.port)
                                        }
                                    }.onSuccess { res ->
                                        onNavigate(Screen.Preview(hostId, row.port, res.path, res.session))
                                    }.onFailure {
                                        error = rpcErrorMessage(it)
                                    }
                                }
                            },
                            onStop = { stopTarget = row },
                        )
                    }
                }
            }
        }
    }
    val stop = stopTarget
    if (stop != null) {
        CenterDialog(
            title = "Stop server?",
            visible = true,
            onDismiss = { stopTarget = null },
            body = "This sends SIGTERM to ${stop.command} on port ${stop.port}.",
            buttons = listOf(
                DialogButtonSpec("Cancel") { stopTarget = null },
                DialogButtonSpec("Stop", destructive = true) {
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) { RemoteGateway.serversKill(hosts, hostId, stop.pid) }
                        }.onSuccess {
                            stopTarget = null
                            refresh()
                        }.onFailure {
                            error = rpcErrorMessage(it)
                            stopTarget = null
                        }
                    }
                },
            ),
        )
    }
}

@Composable
private fun ServerRow(
    row: ServerEntry,
    onOpen: () -> Unit,
    onStop: () -> Unit,
) {
    ActionRow(row.command.ifEmpty { "Process ${row.pid}" }, "Port ${row.port} · ${row.cwd}") {
        AppText(
            "Open",
            Modifier
                .clickable(onClick = onOpen)
                .padding(horizontal = 8.dp),
            style = Type.meta,
            color = Tokens.current.foreground,
        )
        CircleButton(Icons.Stop, glass = false, onClick = onStop, contentDescription = "Stop")
    }
}
