package sh.simple.android.store

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.Flow

@Composable
fun SimpleDatabase.rememberHostsForDisplay(): Flow<List<HostEntity>> =
    remember(this) { hosts().observeForDisplay() }

@Composable
fun SimpleDatabase.rememberAgents(): Flow<List<AgentEntity>> =
    remember(this) { agents().observeAll().distinctList() }

@Composable
fun SimpleDatabase.rememberWorkspaces(): Flow<List<WorkspaceEntity>> =
    remember(this) { workspaces().observeAll().distinctList() }

@Composable
fun SimpleDatabase.rememberItems(): Flow<List<ItemEntity>> =
    remember(this) { items().observeAll().distinctList() }

@Composable
fun SimpleDatabase.rememberMessages(hostId: String, agentId: String): Flow<List<MessageEntity>> =
    remember(this, hostId, agentId) { messages().observeForAgent(hostId, agentId).distinctList() }
