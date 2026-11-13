package sh.simple.android.store

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import sh.simple.android.link.Hosts
import sh.simple.android.model.AgentCommandsResult
import sh.simple.android.model.WireJson

object Commands {
    private val memory = mutableMapOf<String, AgentCommandsResult>()

    fun peek(agentKey: String): AgentCommandsResult? = memory[agentKey]

    suspend fun load(hosts: Hosts, hostId: String, agentId: String): AgentCommandsResult {
        val key = "$hostId:$agentId"
        val raw = hosts.rpc(hostId, "agent.commands", buildJsonObject { put("id", agentId) })
        val result = WireJson.decodeFromJsonElement(AgentCommandsResult.serializer(), raw)
        memory[key] = result
        return result
    }
}
