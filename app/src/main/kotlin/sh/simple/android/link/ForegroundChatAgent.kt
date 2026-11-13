package sh.simple.android.link

object ForegroundChatAgent {
    @Volatile
    private var agentKey: String? = null

    fun set(hostId: String, agentId: String) {
        agentKey = key(hostId, agentId)
    }

    fun clear() {
        agentKey = null
    }

    fun isVisible(hostId: String, agentId: String): Boolean = agentKey == key(hostId, agentId)

    private fun key(hostId: String, agentId: String): String = "$hostId:$agentId"
}
