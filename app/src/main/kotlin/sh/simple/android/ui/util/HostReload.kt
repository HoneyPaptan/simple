package sh.simple.android.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import sh.simple.android.link.HostLinkState
import sh.simple.android.link.Hosts

@Composable
fun HostReconnectReloadEffect(hosts: Hosts, hostId: String, onReload: () -> Unit) {
    LaunchedEffect(hostId) {
        val link = hosts.link(hostId) ?: return@LaunchedEffect
        var wasLive = link.state.value is HostLinkState.Live
        link.state.collect { state ->
            val live = state is HostLinkState.Live
            if (live && !wasLive) {
                onReload()
            }
            wasLive = live
        }
    }
}
