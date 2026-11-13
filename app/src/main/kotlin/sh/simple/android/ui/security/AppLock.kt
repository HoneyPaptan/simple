package sh.simple.android.ui.security

import android.app.KeyguardManager
import android.content.Context
import android.os.SystemClock
import androidx.fragment.app.FragmentActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import sh.simple.android.store.AppSettingsStore
import sh.simple.android.ui.kit.AppText
import sh.simple.android.ui.kit.PrimaryButton
import sh.simple.android.ui.theme.Tokens
import sh.simple.android.ui.theme.Type

private const val lockGraceMs = 60_000L

private var processLock: AppLockState? = null

fun deviceHasScreenLock(context: Context): Boolean {
    val km = context.getSystemService(KeyguardManager::class.java) ?: return false
    return km.isDeviceSecure
}

class AppLockState(
    settings: AppSettingsStore,
    private val context: Context,
) : DefaultLifecycleObserver {
    private var enabled = settings.readAppLockBlocking()
    var locked by mutableStateOf(enabled && deviceHasScreenLock(context))
        private set
    private var backgroundAt = 0L
    private var started = false

    fun onSettingChanged(on: Boolean) {
        enabled = on
        if (!on) locked = false
    }

    fun unlock() {
        locked = false
    }

    override fun onStart(owner: LifecycleOwner) {
        val first = !started
        started = true
        if (!enabled) return
        if (!deviceHasScreenLock(context)) {
            locked = false
            return
        }
        if (first || SystemClock.elapsedRealtime() - backgroundAt >= lockGraceMs) locked = true
    }

    override fun onStop(owner: LifecycleOwner) {
        backgroundAt = SystemClock.elapsedRealtime()
    }
}

@Composable
fun rememberAppLockState(settings: AppSettingsStore): AppLockState {
    val context = LocalContext.current
    return remember {
        processLock ?: AppLockState(settings, context.applicationContext).also {
            processLock = it
            ProcessLifecycleOwner.get().lifecycle.addObserver(it)
        }
    }
}

@Composable
fun AppLockGate(locked: Boolean, onUnlocked: () -> Unit, modifier: Modifier = Modifier) {
    if (!locked) return
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    LaunchedEffect(Unit) { activity?.let { promptUnlock(it, onUnlocked) } }
    Box(
        modifier
            .fillMaxSize()
            .background(Tokens.current.background),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.layout.Column(Modifier.padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AppText("simple is locked", style = Type.headline, color = Tokens.current.foreground)
            PrimaryButton("Unlock", Modifier.padding(top = 20.dp)) {
                if (activity != null) promptUnlock(activity, onUnlocked)
            }
        }
    }
}

private fun promptUnlock(activity: FragmentActivity, onUnlocked: () -> Unit) {
    val allowed = BiometricManager.Authenticators.BIOMETRIC_STRONG or
        BiometricManager.Authenticators.DEVICE_CREDENTIAL
    val executor = ContextCompat.getMainExecutor(activity)
    val prompt = BiometricPrompt(
        activity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onUnlocked()
            }
        },
    )
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Unlock simple")
        .setAllowedAuthenticators(allowed)
        .build()
    prompt.authenticate(info)
}
