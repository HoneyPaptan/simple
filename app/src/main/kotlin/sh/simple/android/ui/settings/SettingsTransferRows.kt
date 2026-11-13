package sh.simple.android.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.simple.android.store.AppSettingsStore
import sh.simple.android.store.HostEntity
import sh.simple.android.store.SettingsExportDocument
import sh.simple.android.store.SettingsTransfer
import sh.simple.android.store.SimpleDatabase
import sh.simple.android.ui.kit.AppText
import sh.simple.android.ui.kit.GlassSheet
import sh.simple.android.ui.kit.Icons
import sh.simple.android.ui.kit.ListRow
import sh.simple.android.ui.kit.PrimaryButton
import sh.simple.android.ui.kit.SectionLabel
import sh.simple.android.ui.theme.AppearanceController
import sh.simple.android.ui.theme.Tokens
import sh.simple.android.link.settingsExportError
import sh.simple.android.link.settingsImportError
import sh.simple.android.ui.theme.Type

@Composable
fun SettingsTransferRows(
    db: SimpleDatabase,
    appSettings: AppSettingsStore,
    appearance: AppearanceController,
    onStatus: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var importPreview by remember { mutableStateOf<SettingsExportDocument?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val hosts = withContext(Dispatchers.IO) { db.hosts().listAll() }
                val json = SettingsTransfer.encode(appSettings.settings, appearance.settings, hosts)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray())
                    } ?: error("write failed")
                }
                onStatus("Settings exported")
            }.onFailure { onStatus(settingsExportError(it)) }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        input.readBytes().decodeToString()
                    } ?: error("read failed")
                }
                importPreview = SettingsTransfer.decode(text)
            }.onFailure { onStatus("Could not import this file. Export it again from the other phone and retry.") }
        }
    }
    val pending = importPreview
    GlassSheet(
        visible = pending != null,
        onDismiss = { importPreview = null },
        title = "Import settings",
        form = true,
    ) {
        if (pending != null) Column {
            AppText(
                SettingsTransfer.previewSummary(pending),
                Modifier.padding(20.dp),
                style = Type.body,
                color = Tokens.current.foreground,
            )
            PrimaryButton("Import") {
                val doc = pending
                importPreview = null
                scope.launch {
                    runCatching {
                        appSettings.setBackgroundAlerts(doc.background_alerts)
                        appearance.update { SettingsTransfer.appearanceFromExport(doc.appearance) }
                        withContext(Dispatchers.IO) {
                            val known = db.hosts().listAll().map { it.fingerprint }.toSet()
                            for (c in doc.computers.filter { it.fingerprint !in known }) {
                                db.hosts().upsert(
                                    HostEntity(
                                        id = "imp_${c.fingerprint.take(16)}",
                                        name = c.name,
                                        addresses = c.addresses,
                                        fingerprint = c.fingerprint,
                                        tokenCipher = "",
                                        epoch = null,
                                        seq = 0,
                                    ),
                                )
                            }
                        }
                        onStatus("Imported. Pair again for computers without a token.")
                    }.onFailure { onStatus(settingsImportError(it)) }
                }
            }
        }
    }
    SectionLabel("Backup")
    ListRow(Icons.File, "Export settings", chevron = false) {
        exportLauncher.launch("simple-settings.json")
    }
    ListRow(Icons.File, "Import settings", chevron = false) {
        importLauncher.launch(arrayOf("application/json"))
    }
}
