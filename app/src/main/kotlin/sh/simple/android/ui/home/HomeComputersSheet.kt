package sh.simple.android.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import sh.simple.android.link.Hosts
import sh.simple.android.ui.kit.AppText
import sh.simple.android.ui.kit.GlassSheet
import sh.simple.android.ui.kit.Icons
import sh.simple.android.ui.kit.ListRow
import sh.simple.android.ui.kit.PrimaryButton
import sh.simple.android.ui.kit.SecondaryButton
import sh.simple.android.ui.kit.SectionLabel
import sh.simple.android.ui.theme.Tokens
import sh.simple.android.ui.theme.Type

@Composable
internal fun HomeComputersSheet(
    open: Boolean,
    onDismiss: () -> Unit,
    hosts: List<sh.simple.android.store.HostEntity>,
    links: Hosts,
    onPair: () -> Unit,
) {
    var selectedId by remember { mutableStateOf<String?>(null) }
    var confirmRemove by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val selected = hosts.firstOrNull { it.id == selectedId }
    LaunchedEffect(open) {
        if (!open) {
            selectedId = null
            confirmRemove = false
        }
    }
    GlassSheet(open, onDismiss, selected?.name ?: "Computers") {
        Column(Modifier.padding(horizontal = 16.dp)) {
            if (selected == null) {
                if (hosts.isNotEmpty()) SectionLabel("Paired")
                for (host in hosts) {
                    androidx.compose.runtime.key(host.id) {
                        val state = links.link(host.id)?.state?.collectAsState()?.value
                        ListRow(Icons.Server, host.name, trailing = hostStateShort(state)) {
                            selectedId = host.id
                            confirmRemove = false
                        }
                    }
                }
                SectionLabel("More")
                ListRow(Icons.Qr, "Pair a computer", onClick = onPair)
            } else {
                val state = links.link(selected.id)?.state?.collectAsState()?.value
                AppText(hostStateLabel(state), Modifier.padding(top = 8.dp), style = Type.body, color = Tokens.current.muted)
                if (state is sh.simple.android.link.HostLinkState.Revoked) {
                    PrimaryButton("Pair again", Modifier.padding(top = 16.dp), onClick = onPair)
                }
                if (!confirmRemove) {
                    SecondaryButton("Remove from this phone", Modifier.padding(top = 12.dp)) { confirmRemove = true }
                } else {
                    AppText(
                        "This phone forgets ${selected.name} and its agents. Nothing changes on the computer.",
                        Modifier.padding(top = 16.dp),
                        style = Type.body,
                    )
                    SecondaryButton("Remove ${selected.name}", Modifier.padding(top = 12.dp)) {
                        val id = selected.id
                        selectedId = null
                        confirmRemove = false
                        scope.launch { links.removeHost(id) }
                    }
                }
                SecondaryButton("Back", Modifier.padding(top = 12.dp, bottom = 16.dp)) {
                    selectedId = null
                    confirmRemove = false
                }
            }
        }
    }
}

private fun hostStateShort(state: sh.simple.android.link.HostLinkState?): String = when (state) {
    sh.simple.android.link.HostLinkState.Live -> "Connected"
    sh.simple.android.link.HostLinkState.Connecting -> "Connecting"
    sh.simple.android.link.HostLinkState.Revoked -> "Pair again"
    else -> "Offline"
}

private fun hostStateLabel(state: sh.simple.android.link.HostLinkState?): String = when (state) {
    sh.simple.android.link.HostLinkState.Live -> "Connected"
    sh.simple.android.link.HostLinkState.Connecting -> "Connecting"
    sh.simple.android.link.HostLinkState.Revoked -> "This phone needs to pair with this computer again. Run simpled pair on it and scan the code."
    is sh.simple.android.link.HostLinkState.Offline -> "Offline, retrying"
    null -> "Offline"
}
