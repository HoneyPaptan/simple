package sh.simple.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import sh.simple.android.ui.SimpleApp

class MainActivity : FragmentActivity() {
    private var pendingPairUri by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        pendingPairUri = pairUriFromIntent(intent)
        val app = application as App
        setContent {
            SimpleApp(app, pendingPairUri)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingPairUri = pairUriFromIntent(intent)
    }

    private fun pairUriFromIntent(intent: Intent?): String? {
        val data: Uri = intent?.data ?: return null
        if (data.scheme == "simple" && data.host == "pair") return data.toString()
        return null
    }
}
