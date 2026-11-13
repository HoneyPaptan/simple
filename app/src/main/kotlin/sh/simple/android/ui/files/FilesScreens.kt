package sh.simple.android.ui.files

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.simple.android.ui.composer.pathWithTilde
import sh.simple.android.link.Hosts
import sh.simple.android.link.RemoteGateway
import sh.simple.android.link.launchRpc
import sh.simple.android.link.rpcErrorMessage
import sh.simple.android.model.FileEntry
import sh.simple.android.ui.composer.formatFileAge
import sh.simple.android.ui.composer.formatFileSize
import sh.simple.android.ui.composer.joinPath
import sh.simple.android.ui.composer.parentPath
import sh.simple.android.ui.glass.GlassStage
import sh.simple.android.ui.kit.AppText
import sh.simple.android.ui.kit.ErrorState
import sh.simple.android.ui.kit.HeaderBar
import sh.simple.android.ui.kit.LoadingRow
import sh.simple.android.ui.kit.Icons
import sh.simple.android.ui.kit.ListRow
import sh.simple.android.ui.kit.GlassSheet
import sh.simple.android.ui.kit.listScrollBottomPadding
import sh.simple.android.ui.theme.LocalCodeStyle
import sh.simple.android.ui.theme.Tokens
import sh.simple.android.ui.theme.Type
import sh.simple.android.ui.util.HostReconnectReloadEffect

@Composable
fun FilesScreen(
    hosts: Hosts,
    hostId: String,
    startPath: String,
    onBack: () -> Unit,
    onOpenFile: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val workspaceRoot = startPath
    var path by remember(startPath) { mutableStateOf(startPath) }
    var entries by remember { mutableStateOf(emptyList<FileEntry>()) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    fun reload() {
        scope.launch {
            error = ""
            entries = withContext(Dispatchers.IO) {
                runCatching { RemoteGateway.filesList(hosts, hostId, path).entries }
                    .getOrElse {
                        error = rpcErrorMessage(it)
                        emptyList()
                    }
            }
        }
    }
    LaunchedEffect(path, hostId) { reload() }
    HostReconnectReloadEffect(hosts, hostId) { reload() }
    GlassStage(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            HeaderBar(listOf(Icons.Back), "Files", emptyList(), subtitle = pathWithTilde(path), onLeftClick = { onBack() })
            if (error.isNotEmpty()) {
                AppText(error, Modifier.padding(20.dp), style = Type.meta, color = Tokens.current.remove)
            }
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = listScrollBottomPadding(),
            ) {
                if (path != workspaceRoot && path != "/" && path != "~") {
                    item {
                        ListRow(Icons.Back, "..", chevron = false) { path = parentPath(path) }
                    }
                }
                val dirs = entries.filter { it.dir }.sortedBy { it.name.lowercase() }
                val files = entries.filter { !it.dir }.sortedBy { it.name.lowercase() }
                items(dirs, key = { "d:${it.name}" }) { entry ->
                    ListRow(Icons.Folder, entry.name, secondary = formatFileAge(entry.mtime_ms), chevron = true) {
                        path = joinPath(path, entry.name)
                    }
                }
                items(files, key = { "f:${it.name}" }) { entry ->
                    ListRow(
                        Icons.File,
                        entry.name,
                        secondary = "${formatFileSize(entry.size)} · ${formatFileAge(entry.mtime_ms)}",
                        chevron = true,
                    ) {
                        onOpenFile(joinPath(path, entry.name))
                    }
                }
            }
        }
    }
}

@Composable
fun FileViewerScreen(
    hosts: Hosts,
    hostId: String,
    path: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    fun reload() {
        scope.launch {
            loading = true
            error = ""
            val result = withContext(Dispatchers.IO) {
                runCatching { RemoteGateway.filesRead(hosts, hostId, path) }
            }
            loading = false
            result.onSuccess {
                text = it.text
                draft = it.text
            }.onFailure { error = rpcErrorMessage(it) }
        }
    }
    LaunchedEffect(path, hostId) { reload() }
    HostReconnectReloadEffect(hosts, hostId) { reload() }
    val title = path.substringAfterLast('/')
    GlassStage(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            HeaderBar(
                listOf(Icons.Back),
                title,
                if (error.isEmpty() && !loading) listOf(if (editing) Icons.Check else Icons.Pencil) else emptyList(),
                onLeftClick = { onBack() },
                onRightClick = {
                    if (editing) {
                        scope.launchRpc {
                            val toWrite = textForWrite(text, draft)
                            val saved = withContext(Dispatchers.IO) {
                                runCatching {
                                    RemoteGateway.filesWrite(hosts, hostId, path, toWrite)
                                }
                            }
                            saved.onSuccess {
                                text = toWrite
                                draft = toWrite
                                editing = false
                                error = ""
                            }.onFailure { error = rpcErrorMessage(it) }
                        }
                    } else {
                        editing = true
                    }
                },
            )
            when {
                loading -> LoadingRow()
                error.isNotEmpty() -> ErrorState(error)
                editing -> {
                    val lineNums = draft.lines()
                    val scroll = rememberScrollState()
                    val lineHeight = with(androidx.compose.ui.platform.LocalDensity.current) {
                        LocalCodeStyle.current.lineHeight.toDp()
                    }
                    Row(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(12.dp)
                            .verticalScroll(scroll),
                        verticalAlignment = androidx.compose.ui.Alignment.Top,
                    ) {
                        Column(Modifier.padding(end = 10.dp)) {
                            lineNums.forEachIndexed { index, _ ->
                                AppText(
                                    "${index + 1}",
                                    Modifier
                                        .width(32.dp)
                                        .heightIn(min = lineHeight, max = lineHeight),
                                    style = LocalCodeStyle.current,
                                    color = Tokens.current.muted.copy(alpha = 0.45f),
                                    maxLines = 1,
                                )
                            }
                        }
                        BasicTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            modifier = Modifier.weight(1f),
                            textStyle = LocalCodeStyle.current.copy(color = Tokens.current.foreground),
                            cursorBrush = SolidColor(Tokens.current.foreground),
                        )
                    }
                }
                else -> FileCodeBody(text)
            }
        }
    }
}

@Composable
private fun FileCodeBody(text: String) {
    val lines = viewerLines(text)
    Column(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        lines.forEachIndexed { index, line ->
            CodeLineRow(index + 1, line)
        }
    }
}

@Composable
private fun CodeLineRow(number: Int, text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 18.dp),
        verticalAlignment = androidx.compose.ui.Alignment.Top,
    ) {
        AppText(
            number.toString(),
            Modifier.width(32.dp),
            style = LocalCodeStyle.current,
            color = Tokens.current.muted.copy(alpha = 0.45f),
            maxLines = 1,
        )
        AppText(
            text,
            Modifier.weight(1f),
            style = LocalCodeStyle.current,
            maxLines = 1,
        )
    }
}
