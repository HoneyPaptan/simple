package sh.simple.android.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.simple.android.link.HostBulk
import sh.simple.android.link.Hosts
import sh.simple.android.link.RemoteGateway
import sh.simple.android.link.rpcErrorMessage
import sh.simple.android.model.FontEntry
import sh.simple.android.store.CodeFontStore
import sh.simple.android.store.HostEntity
import sh.simple.android.store.SimpleDatabase
import sh.simple.android.store.TokenVault
import sh.simple.android.ui.glass.GlassStage
import sh.simple.android.ui.kit.AppText
import sh.simple.android.ui.kit.HeaderBar
import sh.simple.android.ui.kit.Icons
import sh.simple.android.ui.kit.ListRow
import sh.simple.android.ui.kit.SectionLabel
import sh.simple.android.ui.kit.Segmented
import sh.simple.android.ui.kit.listScrollBottomPadding
import sh.simple.android.ui.theme.Tokens
import sh.simple.android.ui.theme.Type

@Composable
fun CodeFontScreen(
    db: SimpleDatabase,
    hosts: Hosts,
    vault: TokenVault,
    store: CodeFontStore,
    hostRows: List<HostEntity>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var mode by remember(store.choice.mode) { mutableStateOf(if (store.choice.mode == "host") "host" else "geist") }
    var pickHost by remember { mutableStateOf<HostEntity?>(null) }
    var fonts by remember { mutableStateOf<List<FontEntry>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    GlassStage(modifier.fillMaxSize().background(Tokens.current.background)) {
        Column(Modifier.fillMaxSize()) {
            HeaderBar(listOf(Icons.Back), "Code font", emptyList(), onLeftClick = { _ ->
                if (pickHost != null) {
                    pickHost = null
                    fonts = emptyList()
                } else {
                    onBack()
                }
            })
            if (store.loadNote.isNotEmpty()) {
                AppText(store.loadNote, Modifier.padding(20.dp), style = Type.meta, color = Tokens.current.muted)
            }
            if (error.isNotEmpty()) {
                AppText(error, Modifier.padding(20.dp), style = Type.meta, color = Tokens.current.remove)
            }
            Segmented(
                selection = mode,
                options = listOf("geist" to "Geist Mono", "host" to "From a computer"),
                select = { choice ->
                    mode = choice
                    if (choice == "geist") {
                        store.setGeist()
                        pickHost = null
                        fonts = emptyList()
                    }
                },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            when {
                mode == "geist" -> {
                    AppText(
                        "Code, diffs and the screen peek use Geist Mono.",
                        Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        style = Type.meta,
                        color = Tokens.current.muted,
                    )
                }
                pickHost == null -> {
                    SectionLabel("Computer")
                    LazyColumn(contentPadding = listScrollBottomPadding()) {
                        items(hostRows, key = { it.id }) { host ->
                            ListRow(Icons.Server, host.name, chevron = true) {
                                pickHost = host
                                busy = true
                                error = ""
                                scope.launch {
                                    runCatching {
                                        withContext(Dispatchers.IO) { RemoteGateway.fontsList(hosts, host.id) }
                                    }.onSuccess {
                                        fonts = it.fonts
                                        busy = false
                                    }.onFailure {
                                        error = rpcErrorMessage(it)
                                        busy = false
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {
                    val host = pickHost!!
                    SectionLabel(host.name)
                    if (busy) {
                        AppText("Loading…", Modifier.padding(20.dp), style = Type.body, color = Tokens.current.muted)
                    } else if (fonts.isEmpty()) {
                        AppText("No monospace fonts found on this computer.", Modifier.padding(20.dp), style = Type.body, color = Tokens.current.muted)
                    } else {
                        LazyColumn(contentPadding = listScrollBottomPadding()) {
                            items(fonts, key = { it.path }) { font ->
                                ListRow(Icons.File, font.family, chevron = false) {
                                    if (host.tokenCipher.isEmpty()) {
                                        error = "Pair with this computer first."
                                        return@ListRow
                                    }
                                    busy = true
                                    error = ""
                                    scope.launch {
                                        runCatching {
                                            val bytes = withContext(Dispatchers.IO) {
                                                HostBulk.getFile(host, vault, font.path)
                                            }
                                            store.setHostFont(host.id, font.path, font.family, bytes)
                                            busy = false
                                        }.onFailure {
                                            error = rpcErrorMessage(it)
                                            busy = false
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
