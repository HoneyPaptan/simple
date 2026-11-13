package sh.simple.android.ui.servers

import android.annotation.SuppressLint
import android.net.http.SslCertificate
import android.net.http.SslError
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.security.MessageDigest
import java.security.cert.X509Certificate
import sh.simple.android.store.SimpleDatabase
import sh.simple.android.ui.glass.GlassStage
import sh.simple.android.ui.kit.AppText
import sh.simple.android.ui.kit.HeaderBar
import sh.simple.android.ui.kit.Icons
import sh.simple.android.ui.theme.Tokens
import sh.simple.android.ui.theme.Type

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PreviewScreen(
    db: SimpleDatabase,
    hostId: String,
    port: Int,
    previewPath: String,
    session: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hosts by db.hosts().observeAll().collectAsState(initial = emptyList())
    val host = hosts.firstOrNull { it.id == hostId }
    var loadUrl by remember(hostId, previewPath) { mutableStateOf<String?>(null) }
    var fingerprint by remember(hostId) { mutableStateOf("") }
    var webView by remember { mutableStateOf<WebView?>(null) }
    LaunchedEffect(host) {
        val row = host ?: return@LaunchedEffect
        fingerprint = row.fingerprint
        val addr = row.addresses.split(',').firstOrNull()?.trim().orEmpty()
        if (addr.isNotEmpty()) {
            val origin = "https://$addr"
            CookieManager.getInstance().setCookie(origin, "simple_preview=$session; Path=/; Secure; HttpOnly")
            loadUrl = "$origin$previewPath"
        }
    }
    GlassStage(modifier.fillMaxSize().background(Tokens.current.background)) {
        Column(Modifier.fillMaxSize()) {
            HeaderBar(
                listOf(Icons.Back),
                "Preview",
                listOf(Icons.Refresh),
                subtitle = "Port $port",
                onLeftClick = { _ -> onBack() },
                onRightClick = { webView?.reload() },
            )
            val url = loadUrl
            Box(Modifier.fillMaxSize()) {
                if (url == null || fingerprint.isEmpty()) {
                    AppText("Host not found", Modifier.padding(20.dp), style = Type.body, color = Tokens.current.muted)
                } else {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.allowFileAccess = false
                                settings.allowFileAccessFromFileURLs = false
                                settings.allowUniversalAccessFromFileURLs = false
                                webViewClient = object : WebViewClient() {
                                    override fun onReceivedSslError(
                                        view: WebView?,
                                        handler: SslErrorHandler?,
                                        error: SslError?,
                                    ) {
                                        val cert = error?.certificate
                                        if (cert != null && certMatches(cert, fingerprint)) {
                                            handler?.proceed()
                                        } else {
                                            handler?.cancel()
                                        }
                                    }
                                }
                                loadUrl(url)
                                webView = this
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        update = { view -> webView = view },
                    )
                }
            }
        }
    }
}

private fun certMatches(cert: SslCertificate, fingerprintLowerHex: String): Boolean {
    val x509 = x509FromSslCertificate(cert) ?: return false
    val digest = MessageDigest.getInstance("SHA-256").digest(x509.encoded)
    val fp = digest.joinToString("") { b -> "%02x".format(b) }
    return fp == fingerprintLowerHex.lowercase()
}

private fun x509FromSslCertificate(cert: SslCertificate): X509Certificate? {
    return try {
        val method = SslCertificate::class.java.getMethod("getX509Certificate")
        method.invoke(cert) as? X509Certificate
    } catch (_: ReflectiveOperationException) {
        null
    }
}
