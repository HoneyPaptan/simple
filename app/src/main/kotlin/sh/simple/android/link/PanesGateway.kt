package sh.simple.android.link

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import sh.simple.android.model.PanesListResult
import sh.simple.android.model.PanesPeekResult
import sh.simple.android.model.PanesSpawnResult
import sh.simple.android.model.WireJson

object PanesGateway {
    suspend fun list(hosts: Hosts, hostId: String, workspace: String): PanesListResult {
        val raw = hosts.rpc(
            hostId,
            "panes.list",
            buildJsonObject { put("workspace", workspace) },
        )
        return WireJson.decodeFromJsonElement(PanesListResult.serializer(), raw)
    }

    suspend fun peek(hosts: Hosts, hostId: String, ids: List<String>, lines: Int = 20): PanesPeekResult {
        if (ids.isEmpty()) return PanesPeekResult()
        val raw = hosts.rpc(
            hostId,
            "panes.peek",
            buildJsonObject {
                putJsonArray("ids") {
                    ids.forEach { add(JsonPrimitive(it)) }
                }
                put("lines", lines)
            },
        )
        return WireJson.decodeFromJsonElement(PanesPeekResult.serializer(), raw)
    }

    suspend fun spawn(hosts: Hosts, hostId: String, workspace: String): PanesSpawnResult {
        val raw = hosts.rpc(
            hostId,
            "panes.spawn",
            buildJsonObject { put("workspace", workspace) },
        )
        return WireJson.decodeFromJsonElement(PanesSpawnResult.serializer(), raw)
    }
}
