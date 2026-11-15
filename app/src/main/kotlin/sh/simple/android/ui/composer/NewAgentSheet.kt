package sh.simple.android.ui.composer

import sh.simple.android.link.HostRuntimes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.simple.android.link.rpcErrorMessage
import sh.simple.android.link.rpcRun
import sh.simple.android.link.Hosts
import sh.simple.android.link.RemoteGateway
import sh.simple.android.store.Models
import sh.simple.android.store.modelDisplayLabel
import sh.simple.android.model.AgentModelsResult
import sh.simple.android.store.SimpleDatabase
import sh.simple.android.store.Sync
import sh.simple.android.store.TokenVault
import sh.simple.android.store.WorkspaceEntity
import sh.simple.android.store.resetAgentDraftForCatalog
import sh.simple.android.store.startModelForKind
import sh.simple.android.ui.kit.AppText
import sh.simple.android.ui.kit.GlassSheet
import sh.simple.android.ui.kit.Icons
import sh.simple.android.ui.kit.ListRow
import sh.simple.android.ui.kit.SectionLabel
import sh.simple.android.ui.kit.rememberHideKeyboard
import sh.simple.android.ui.model.ModelPickerSheets
import sh.simple.android.ui.model.modeMenuLabel
import sh.simple.android.ui.theme.Tokens
import sh.simple.android.ui.theme.Type
import sh.simple.android.ui.chat.ChatOutboundStore
import sh.simple.android.ui.chat.persistMessageImages
import sh.simple.android.ui.util.agentKindIcon
import sh.simple.android.ui.util.agentKindLabel
import sh.simple.android.ui.util.installedNewAgentKinds
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class NewAgentTarget(
    val hostId: String,
    val cwd: String,
    val label: String,
)

@Composable
fun NewAgentConfigureSheet(
    open: Boolean,
    onDismiss: () -> Unit,
    prompt: String,
    hosts: Hosts,
    db: SimpleDatabase,
    targets: List<NewAgentTarget>,
    initialTarget: NewAgentTarget?,
    attachments: List<ComposerAttachment> = emptyList(),
    vault: TokenVault,
    initialModel: String? = null,
    initialEffort: String = "high",
    initialMode: String = "ask",
    initialKind: String? = null,
    chatOutbound: ChatOutboundStore? = null,
    onStarted: (String, String) -> Unit,
) {
    if (!open) return
    val appContext = LocalContext.current
    val scope = rememberCoroutineScope()
    val hideKeyboard = rememberHideKeyboard()
    val sync = remember { Sync(db) }
    var target by remember(open, initialTarget) { mutableStateOf(initialTarget ?: targets.firstOrNull()) }
    var kind by remember(open, initialKind) { mutableStateOf(initialKind ?: "claude") }
    var draftModel by remember { mutableStateOf(initialModel) }
    var draftEffort by remember { mutableStateOf(initialEffort) }
    var draftMode by remember { mutableStateOf(initialMode) }
    var modeLabel by remember { mutableStateOf(modeMenuLabel(initialMode)) }
    var modelLabel by remember { mutableStateOf("Model") }
    var catalog by remember { mutableStateOf<AgentModelsResult?>(null) }
    var modeOptions by remember { mutableStateOf(listOf("ask", "plan", "auto", "full")) }
    var worktreeOn by remember { mutableStateOf(false) }
    var inHerdr by remember { mutableStateOf(false) }
    var branch by remember { mutableStateOf("") }
    var workspaceOpen by remember { mutableStateOf(false) }
    var folderOpen by remember { mutableStateOf(false) }
    var modelOpen by remember { mutableStateOf(false) }
    var modeMenuOpen by remember { mutableStateOf(false) }
    var agentPickerOpen by remember { mutableStateOf(false) }
    var installedKinds by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    val allAgents by db.agents().observeAll().collectAsState(initial = emptyList())
    val allWorkspaces by db.workspaces().observeAll().collectAsState(initial = emptyList())
    var hostLabel by remember { mutableStateOf("") }
    var starting by remember { mutableStateOf(false) }
    var startError by remember(open) { mutableStateOf("") }
    LaunchedEffect(open, agentPickerOpen, modelOpen, modeMenuOpen, workspaceOpen, folderOpen) {
        if (open && (agentPickerOpen || modelOpen || modeMenuOpen || workspaceOpen || folderOpen)) {
            hideKeyboard()
        }
    }
    LaunchedEffect(open) {
        if (open) hideKeyboard()
    }
    val hostId = target?.hostId.orEmpty()
    val recentFolderPaths = remember(hostId, allAgents, allWorkspaces) {
        recentDirectoriesForHost(hostId, allWorkspaces, allAgents)
    }
    val agentInstalled = installedKinds[kind] != false
    LaunchedEffect(hostId) {
        if (hostId.isEmpty()) {
            installedKinds = emptyMap()
            hostLabel = ""
            return@LaunchedEffect
        }
        hostLabel = withContext(Dispatchers.IO) { db.hosts().get(hostId)?.name ?: hostId }
        installedKinds = withContext(Dispatchers.IO) {
            runCatching { RemoteGateway.installedKinds(hosts, hostId).kinds }.getOrDefault(emptyMap())
        }
    }
    LaunchedEffect(hostId, installedKinds) {
        val usable = installedNewAgentKinds(installedKinds)
        if (kind !in usable) usable.firstOrNull()?.let { kind = it }
    }
    LaunchedEffect(hostId, kind) {
        if (hostId.isEmpty()) return@LaunchedEffect
        val loaded = withContext(Dispatchers.IO) {
            runCatching {
                val loadedCatalog = Models.load(hosts, db, hostId, kind)
                catalog = loadedCatalog
                val reset = resetAgentDraftForCatalog(loadedCatalog, kind, draftModel, draftMode)
                draftModel = reset.model
                draftMode = reset.mode
                modeLabel = reset.modeLabel
                modelLabel = reset.modelLabel
                modeOptions = reset.modeOptions
            }
        }
        loaded.onFailure { startError = rpcErrorMessage(it) }
    }
    fun start() {
        val picked = target ?: return
        if (starting || !agentInstalled) return
        starting = true
        startError = ""
        scope.launch {
            rpcRun {
                val agent = withContext(Dispatchers.IO) {
                    val catalog = Models.load(hosts, db, picked.hostId, kind)
                    val model = startModelForKind(kind, catalog, draftModel)
                    val paths = uploadComposerAttachments(
                        hosts,
                        vault,
                        db,
                        picked.hostId,
                        picked.cwd,
                        attachments,
                    )
                    val startPrompt = RemoteGateway.promptWithAttachmentPaths(prompt, paths)
                    RemoteGateway.startAgent(
                        hosts,
                        picked.hostId,
                        kind,
                        picked.cwd,
                        draftMode,
                        startPrompt.ifBlank { null },
                        model,
                        if (kind == "claude" || kind == "fx") draftEffort else null,
                        if (worktreeOn) branch.trim().ifEmpty { "simple/${System.currentTimeMillis()}" } else null,
                        if (inHerdr && HostRuntimes.hasBoth(picked.hostId)) "herdr" else null,
                    )
                }
                sync.upsertAgent(picked.hostId, agent)
                val outbound = chatOutbound
                if (outbound != null && (prompt.isNotBlank() || attachments.isNotEmpty())) {
                    val paths = attachments.map { it.localPath }
                    val localId = outbound.stage(picked.hostId, agent.id, prompt, paths)
                    outbound.rememberDelivery(picked.hostId, agent.id, prompt, paths, picked.cwd)
                    persistMessageImages(
                        appContext,
                        db.messageImages(),
                        picked.hostId,
                        agent.id,
                        localId,
                        paths,
                    )
                }
                onStarted(picked.hostId, agent.id)
                onDismiss()
            }.onFailure { startError = rpcErrorMessage(it) }
            starting = false
        }
    }
    GlassSheet(
        visible = true,
        onDismiss = onDismiss,
        title = "New agent",
        doneLabel = if (starting) "..." else "Start",
        onDone = { if (agentInstalled) start() },
        stacked = true,
    ) {
        Column(Modifier.fillMaxWidth()) {
            if (startError.isNotEmpty()) {
                AppText(startError, Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = Type.meta, color = Tokens.current.remove)
            }
            ListRow(
                Icons.Folder,
                "Workspace",
                secondary = target?.cwd?.let(::pathWithTilde) ?: "Choose",
                chevron = true,
                secondaryOverflow = TextOverflow.StartEllipsis,
                onClick = { workspaceOpen = true },
            )
            ListRow(
                agentKindIcon(kind),
                "Agent",
                secondary = agentKindLabel(kind),
                chevron = true,
                onClick = { agentPickerOpen = true },
            )
            if (kind != "shell") {
                ListRow(Icons.Sliders, "Model", secondary = modelLabel, chevron = true, onClick = { modelOpen = true })
            }
            if (modeOptions.isNotEmpty()) {
                ListRow(Icons.Shield, "Permission", secondary = modeLabel, chevron = true, onClick = { modeMenuOpen = true })
            }
            if (target?.hostId?.let(HostRuntimes::hasBoth) == true) {
                ListRow(
                    Icons.Server,
                    "Runs in",
                    secondary = if (inHerdr) "herdr" else "herdr-core",
                    chevron = false,
                    onClick = { inHerdr = !inHerdr },
                )
            }
            ListRow(
                Icons.Branch,
                "New worktree",
                secondary = if (worktreeOn) "On" else "Off",
                chevron = false,
                onClick = { worktreeOn = !worktreeOn },
            )
            if (worktreeOn) {
                BasicTextField(
                    value = branch,
                    onValueChange = { branch = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    textStyle = Type.body.copy(color = Tokens.current.foreground),
                    cursorBrush = SolidColor(Tokens.current.foreground),
                    decorationBox = { inner ->
                        if (branch.isEmpty()) {
                            sh.simple.android.ui.kit.AppText("Branch name", style = Type.body, color = Tokens.current.muted)
                        }
                        inner()
                    },
                )
            }
        }
    }
    WorkspacePickerSheet(
        open = workspaceOpen,
        onDismiss = { workspaceOpen = false },
        targets = targets,
        onPick = { target = it },
        onChooseFolder = { folderOpen = true },
    )
    FolderBrowserSheet(
        open = folderOpen,
        onDismiss = { folderOpen = false },
        hosts = hosts,
        hostId = hostId,
        recentPaths = recentFolderPaths,
        onPick = { path, label ->
            if (hostId.isNotEmpty()) {
                target = NewAgentTarget(hostId, path, label)
            }
            folderOpen = false
        },
    )
    if (hostId.isNotEmpty()) {
        ModelPickerSheets(
            open = modelOpen,
            onDismiss = { modelOpen = false },
            hosts = hosts,
            db = db,
            hostId = hostId,
            kind = kind,
            agentId = null,
            currentModel = draftModel,
            currentEffort = draftEffort,
            onDraftChange = { model, effort ->
                if (model != null) {
                    draftModel = model
                    catalog?.let { modelLabel = modelDisplayLabel(it, model) }
                }
                if (effort != null) draftEffort = effort
            },
        )
    }
    AgentKindPickerSheet(
        open = agentPickerOpen,
        onDismiss = { agentPickerOpen = false },
        installed = installedKinds,
        current = kind,
        onPick = { picked ->
            kind = picked
            agentPickerOpen = false
        },
    )
    ModePickerSheet(
        open = modeMenuOpen,
        onDismiss = { modeMenuOpen = false },
        modes = modeOptions,
        current = draftMode,
        onPick = { picked ->
            draftMode = picked.lowercase()
            modeLabel = modeMenuLabel(draftMode)
        },
    )
}

@Composable
fun AgentKindPickerSheet(
    open: Boolean,
    onDismiss: () -> Unit,
    installed: Map<String, Boolean>,
    current: String,
    onPick: (String) -> Unit,
) {
    val rows = installedNewAgentKinds(installed)
    GlassSheet(open, onDismiss, "Agent", stacked = true) {
        LazyColumn {
            items(rows) { rowKind ->
                ListRow(
                    agentKindIcon(rowKind),
                    agentKindLabel(rowKind),
                    chevron = false,
                    selected = rowKind.equals(current, true),
                ) {
                    onPick(rowKind)
                    onDismiss()
                }
            }
        }
    }
}

@Composable
private fun ModePickerSheet(
    open: Boolean,
    onDismiss: () -> Unit,
    modes: List<String>,
    current: String,
    onPick: (String) -> Unit,
) {
    GlassSheet(open, onDismiss, "Permission", stacked = true) {
        LazyColumn {
            items(modes) { mode ->
                ListRow(
                    Icons.Shield,
                    modeMenuLabel(mode),
                    chevron = false,
                    selected = mode.equals(current, true),
                ) {
                    onPick(mode)
                    onDismiss()
                }
            }
        }
    }
}

@Composable
private fun WorkspacePickerSheet(
    open: Boolean,
    onDismiss: () -> Unit,
    targets: List<NewAgentTarget>,
    onPick: (NewAgentTarget) -> Unit,
    onChooseFolder: () -> Unit,
) {
    GlassSheet(open, onDismiss, "Workspace", stacked = true) {
        LazyColumn {
            items(targets, key = { "${it.hostId}:${it.cwd}" }) { row ->
                ListRow(Icons.Folder, row.label, secondary = row.cwd, chevron = false, secondaryOverflow = TextOverflow.StartEllipsis) {
                    onPick(row)
                    onDismiss()
                }
            }
            item {
                ListRow(Icons.FolderPlus, "Choose folder", chevron = true, onClick = onChooseFolder)
            }
        }
    }
}

@Composable
fun FolderBrowserSheet(
    open: Boolean,
    onDismiss: () -> Unit,
    hosts: Hosts,
    hostId: String,
    startPath: String = "~",
    recentPaths: List<String> = emptyList(),
    onPick: (String, String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var path by remember(open) { mutableStateOf(startPath) }
    var entries by remember(hostId, path) {
        mutableStateOf(FolderListCache.get(hostId, path).orEmpty())
    }
    var error by remember { mutableStateOf("") }
    var recentShown by remember { mutableStateOf(recentPaths) }
    var showHidden by remember { mutableStateOf(false) }
    var newFolderEditing by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var creatingFolder by remember { mutableStateOf(false) }
    val newFolderFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val listing = remember(entries) { folderDirListing(entries) }
    LaunchedEffect(newFolderEditing) {
        if (newFolderEditing) {
            newFolderFocus.requestFocus()
            keyboard?.show()
        }
    }
    LaunchedEffect(open, hostId, recentPaths) {
        if (!open || hostId.isEmpty()) {
            recentShown = recentPaths
            return@LaunchedEffect
        }
        if (recentPaths.isEmpty()) {
            recentShown = emptyList()
            return@LaunchedEffect
        }
        recentShown = filterRecentPaths(recentPaths, existingPathsAmong(hosts, hostId, recentPaths))
    }
    LaunchedEffect(open, path, hostId) {
        if (!open || hostId.isEmpty()) return@LaunchedEffect
        FolderListCache.get(hostId, path)?.let { entries = it }
        error = ""
        val fetched = withContext(Dispatchers.IO) {
            runCatching { RemoteGateway.filesList(hosts, hostId, path).entries }
                .getOrElse {
                    error = rpcErrorMessage(it)
                    null
                }
        }
        if (fetched != null) {
            entries = fetched
            FolderListCache.put(hostId, path, fetched)
        }
    }
    fun createFolder() {
        val name = newFolderName.trim()
        if (name.isEmpty() || creatingFolder) return
        creatingFolder = true
        scope.launch {
            rpcRun {
                withContext(Dispatchers.IO) {
                    RemoteGateway.filesMkdir(hosts, hostId, path, name)
                }
                path = joinPath(path, name)
                newFolderName = ""
                newFolderEditing = false
            }.onFailure { error = rpcErrorMessage(it) }
            creatingFolder = false
        }
    }
    GlassSheet(
        open,
        onDismiss,
        "Choose folder",
        doneLabel = "Select",
        onDone = { onPick(path, path.substringAfterLast('/').ifEmpty { path }); onDismiss() },
        stacked = true,
    ) {
        Column {
            if (error.isNotEmpty()) {
                sh.simple.android.ui.kit.AppText(
                    error,
                    Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    style = Type.meta,
                    color = Tokens.current.remove,
                )
            }
            sh.simple.android.ui.kit.AppText(
                path,
                Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style = Type.meta,
                color = Tokens.current.muted,
                maxLines = 2,
            )
            LazyColumn {
                if (recentShown.isNotEmpty()) {
                    item { SectionLabel("Recent") }
                    items(recentShown, key = { "recent:$it" }) { recent ->
                        ListRow(Icons.Folder, pathWithTilde(recent), chevron = false) {
                            onPick(recent, recent.substringAfterLast('/').ifEmpty { recent })
                            onDismiss()
                        }
                    }
                }
                if (path != "/" && path != "~") {
                    item {
                        ListRow(Icons.Back, "..", chevron = false) {
                            path = parentPath(path)
                            newFolderEditing = false
                        }
                    }
                }
                item {
                    if (newFolderEditing) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                            BasicTextField(
                                value = newFolderName,
                                onValueChange = { newFolderName = it },
                                modifier = Modifier.fillMaxWidth().focusRequester(newFolderFocus),
                                textStyle = Type.body.copy(color = Tokens.current.foreground),
                                cursorBrush = SolidColor(Tokens.current.foreground),
                                decorationBox = { inner ->
                                    if (newFolderName.isEmpty()) {
                                        AppText("Folder name", style = Type.body, color = Tokens.current.muted)
                                    }
                                    inner()
                                },
                            )
                            Row(Modifier.padding(top = 8.dp)) {
                                ListRow(
                                    Icons.FolderPlus,
                                    if (creatingFolder) "..." else "Create",
                                    chevron = false,
                                    onClick = { createFolder() },
                                )
                            }
                        }
                    } else {
                        ListRow(Icons.FolderPlus, "New folder", chevron = false) {
                            newFolderEditing = true
                        }
                    }
                }
                items(listing.visible, key = { "v:${it.name}" }) { entry ->
                    ListRow(Icons.Folder, entry.name, chevron = true) {
                        path = joinPath(path, entry.name)
                        newFolderEditing = false
                    }
                }
                if (listing.hidden.isNotEmpty()) {
                    item {
                        ListRow(
                            Icons.Eye,
                            "Hidden folders",
                            secondary = if (showHidden) "On" else "Off",
                            chevron = false,
                            onClick = { showHidden = !showHidden },
                        )
                    }
                    if (showHidden) {
                        items(listing.hidden, key = { "h:${it.name}" }) { entry ->
                            ListRow(Icons.Folder, entry.name, chevron = true) {
                                path = joinPath(path, entry.name)
                                newFolderEditing = false
                            }
                        }
                    }
                }
            }
        }
    }
}

fun targetsFromWorkspaces(
    hostNames: Map<String, String>,
    workspaces: List<WorkspaceEntity>,
    defaultHostId: String,
    showComputerNames: Boolean = sh.simple.android.ui.util.showPairedComputerNames(hostNames.size),
): List<NewAgentTarget> {
    val rows = workspaces.map { ws ->
        val host = sh.simple.android.ui.util.optionalComputerName(ws.hostId, hostNames, showComputerNames)
        val label = if (host == null) ws.name else "${ws.name} · $host"
        NewAgentTarget(ws.hostId, ws.path, label)
    }
    if (rows.isNotEmpty()) return rows
    if (defaultHostId.isNotEmpty()) {
        return listOf(NewAgentTarget(defaultHostId, "~", "Home"))
    }
    return emptyList()
}

fun parentPath(path: String): String {
    if (path == "~") return "~"
    val trimmed = path.trimEnd('/')
    val idx = trimmed.lastIndexOf('/')
    return if (idx <= 0) "/" else trimmed.substring(0, idx)
}

fun joinPath(base: String, name: String): String {
    if (base == "~") return "~/$name"
    return base.trimEnd('/') + "/" + name
}

fun formatFileAge(mtimeMs: Long): String {
    val fmt = SimpleDateFormat("MMM d", Locale.US)
    return fmt.format(Date(mtimeMs))
}

fun formatFileSize(size: Long): String {
    if (size < 1024) return "$size B"
    if (size < 1024 * 1024) return "${size / 1024} KB"
    return "${size / (1024 * 1024)} MB"
}
