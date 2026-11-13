package sh.simple.android.ui.pair

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import com.google.zxing.BarcodeFormat
import com.google.zxing.client.android.Intents
import kotlinx.coroutines.launch
import sh.simple.android.link.parsePairUri
import sh.simple.android.link.Pairing
import sh.simple.android.link.PairingException
import sh.simple.android.link.Hosts
import sh.simple.android.store.SimpleDatabase
import sh.simple.android.store.TokenVault
import sh.simple.android.store.savePairedHost
import sh.simple.android.ui.glass.GlassStage
import sh.simple.android.ui.kit.AppText
import sh.simple.android.ui.kit.GlassSheet
import com.kyant.shapes.RoundedRectangle
import sh.simple.android.ui.kit.PrimaryButton
import sh.simple.android.ui.kit.HeaderBar
import sh.simple.android.ui.kit.Icons
import sh.simple.android.ui.kit.SecondaryButton
import sh.simple.android.ui.theme.LocalCodeStyle
import sh.simple.android.ui.theme.Tokens
import sh.simple.android.ui.theme.Type

@Composable
fun PairScreen(
    hosts: Hosts,
    db: SimpleDatabase,
    vault: TokenVault,
    deviceName: String,
    deviceModel: String,
    onDone: () -> Unit,
    onBack: () -> Unit,
    manualEntry: Boolean = false,
    initialUri: String? = null,
    modifier: Modifier = Modifier,
) {
    var sheet by remember { mutableStateOf(manualEntry) }
    var paste by remember { mutableStateOf(initialUri.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var granted by remember { mutableStateOf(false) }
    var cameraBlocked by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf<String?>(null) }
    var lastRaw by remember { mutableStateOf<String?>(null) }
    var lastUri by remember { mutableStateOf<sh.simple.android.link.PairUri?>(null) }
    var torch by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        cameraBlocked = !ok && context.findActivity()?.let {
            !ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA)
        } == true
    }
    LifecycleResumeEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            granted = true
            cameraBlocked = false
        }
        onPauseOrDispose {}
    }
    val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    DisposableEffect(manualEntry) {
        if (manualEntry) {
            onDispose {}
        } else {
            val ok = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            if (ok) granted = true else permission.launch(Manifest.permission.CAMERA)
            onDispose {}
        }
    }
    LaunchedEffect(initialUri) {
        if (!initialUri.isNullOrBlank()) {
            paste = initialUri
            sheet = true
        }
    }
    fun finish(uri: sh.simple.android.link.PairUri) {
        if (busy != null) return
        busy = uri.hostName
        lastUri = null
        scope.launch {
            error = null
            try {
                val result = Pairing.pair(uri, deviceName, deviceModel, vault) { fp -> db.hosts().listAll().firstOrNull { it.fingerprint == fp } }
                savePairedHost(db, vault, result)
                db.hosts().listAll()
                    .filter { it.fingerprint == result.fingerprint && it.id != result.hostId }
                    .forEach { hosts.removeHost(it.id) }
                hosts.refreshAfterPair()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val ok = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (!ok) {
                        runCatching { notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }
                    }
                }
                onDone()
            } catch (e: PairingException) {
                error = pairErrorMessage(e.kind, uri.hostName)
                if (e.kind == "unreachable") lastUri = uri
            } catch (_: Throwable) {
                error = pairErrorMessage("unreachable", uri.hostName)
                lastUri = uri
            } finally {
                busy = null
            }
        }
    }
    GlassStage(modifier.fillMaxSize().background(Tokens.current.background)) {
        HeaderBar(
            listOf(Icons.X),
            "Pair",
            if (granted && !manualEntry) listOf(Icons.Bolt) else emptyList(),
            onLeftClick = { _ -> onBack() },
            onRightClick = { torch = !torch },
        )
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (!manualEntry) {
                if (granted) {
                    QrPreview(
                        torch = torch,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(top = 72.dp, start = 40.dp, end = 40.dp)
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedRectangle(24.dp)),
                        onCode = { raw ->
                            if (busy == null && raw != lastRaw) {
                                lastRaw = raw
                                val uri = runCatching { parsePairUri(raw) }.getOrNull()
                                if (uri != null) finish(uri) else error = NOT_A_PAIR_CODE
                            }
                        },
                    )
                } else {
                    Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        AppText(
                            if (cameraBlocked) "Camera access is off for simple. Turn it on in settings to scan the code." else "simple needs the camera to scan the pairing code.",
                            style = Type.body,
                            color = Tokens.current.muted,
                            textAlign = TextAlign.Center,
                        )
                        PrimaryButton(if (cameraBlocked) "Open settings" else "Allow camera", Modifier.padding(top = 16.dp)) {
                            if (cameraBlocked) openAppSettings(context) else permission.launch(Manifest.permission.CAMERA)
                        }
                    }
                }
            } else if (!sheet && busy == null) {
                Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    AppText(
                        "Paste the pairing link that simpled pair prints on your computer.",
                        style = Type.body,
                        color = Tokens.current.muted,
                        textAlign = TextAlign.Center,
                    )
                    PrimaryButton("Enter code", Modifier.padding(top = 16.dp)) { sheet = true }
                }
            }
        }
        if (!manualEntry) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
            ) {
                val pairing = busy
                if (pairing != null) {
                    AppText("Pairing with $pairing...", style = Type.body)
                } else {
                    AppText("On your computer, run simpled pair and scan the code it shows.", style = Type.body)
                    SecondaryButton("Enter code instead", Modifier.padding(top = 16.dp)) { sheet = true }
                }
                PairErrorLine(error, retry = lastUri.takeIf { pairing == null }?.let { uri -> { finish(uri) } })
            }
        } else {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 32.dp)
                    .padding(bottom = 40.dp),
            ) {
                busy?.let { AppText("Pairing with $it...", style = Type.body) }
                PairErrorLine(error, retry = lastUri.takeIf { busy == null }?.let { uri -> { finish(uri) } })
            }
        }
    }
    fun submitPaste() {
        val uri = runCatching { parsePairUri(paste.trim()) }.getOrNull()
        sheet = false
        if (uri != null) finish(uri) else error = NOT_A_PAIR_CODE
    }
    GlassSheet(sheet, { sheet = false }, "Pairing code", doneLabel = "Pair", onDone = { submitPaste() }) {
        Column(Modifier.fillMaxWidth()) {
            BasicTextField(
                paste,
                { paste = it },
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 23.dp * 3)
                    .background(Tokens.current.raised, RoundedRectangle(14.dp))
                    .padding(14.dp),
                textStyle = LocalCodeStyle.current.copy(color = Tokens.current.foreground),
                cursorBrush = SolidColor(Tokens.current.foreground),
                minLines = 3,
            )
        }
    }
}

@Composable
private fun QrPreview(torch: Boolean, modifier: Modifier, onCode: (String) -> Unit) {
    val callback = remember {
        object : BarcodeCallback {
            override fun barcodeResult(result: BarcodeResult?) {
                result?.text?.let(onCode)
            }
        }
    }
    AndroidView(
        factory = { ctx ->
            DecoratedBarcodeView(ctx).apply {
                statusView.visibility = android.view.View.GONE
                decoderFactory = pairScanDecoderFactory()
                decodeContinuous(callback)
                resume()
            }
        },
        modifier = modifier,
        update = { view -> if (torch) view.setTorchOn() else view.setTorchOff() },
        onRelease = { it.pause() },
    )
}

fun pairScanDecoderFactory() = DefaultDecoderFactory(listOf(BarcodeFormat.QR_CODE), null, null, Intents.Scan.MIXED_SCAN)

private const val NOT_A_PAIR_CODE = "This is not a simple pairing code. Run simpled pair on your computer and use the code it shows."

fun pairErrorMessage(kind: String, hostName: String): String = when (kind) {
    "bad_code", "expired", "invalid_code" -> "This code is wrong or has expired. Run simpled pair again for a new one."
    "used" -> "This code was already used. Run simpled pair again for a new one."
    "cert_mismatch", "fingerprint" -> "$hostName did not prove it is the computer in the code. Run simpled pair again on the computer you want to add."
    "unreachable" -> "Can't reach $hostName. Check that it is on and that this phone is on the same network or Tailscale."
    else -> "Pairing did not finish. Run simpled pair again and use the new code."
}

@Composable
private fun PairErrorLine(error: String?, retry: (() -> Unit)?) {
    if (error == null) return
    AppText(error, Modifier.padding(top = 12.dp), style = Type.meta, color = Tokens.current.remove)
    if (retry != null) SecondaryButton("Try again", Modifier.padding(top = 10.dp), onClick = retry)
}

private fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
