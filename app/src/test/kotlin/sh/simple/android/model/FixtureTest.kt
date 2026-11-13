package sh.simple.android.model

import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertNotNull
import org.junit.Test

class FixtureTest {
    private fun read(name: String): String {
        val root = File(System.getProperty("user.dir")).parentFile!!.resolve("proto/fixtures")
        return root.resolve(name).readText()
    }

    @Test
    fun decodeAllFixtures() {
        WireJson.decodeFromString<HelloRequest>(read("ws-hello-request.json"))
        WireJson.decodeFromString<HelloResult>(read("ws-hello-result.json"))
        WireJson.decodeFromString<Agent>(read("agent.json"))
        WireJson.decodeFromString<AgentModelsResult>(read("agent-models-result.json"))
        WireJson.decodeFromString<AgentCommandsResult>(read("agent-commands-result.json"))
        WireJson.decodeFromString<FilesSearchResult>(read("files-search-result.json"))
        WireJson.decodeFromString<AgentKindsResult>(read("agent-kinds-result.json"))
        WireJson.decodeFromString<AgentStartResult>(read("agent-start-result.json"))
        WireJson.decodeFromString<FilesListResult>(read("files-list-result.json"))
        WireJson.decodeFromString<JsonObject>(read("files-mkdir-result.json"))
        WireJson.decodeFromString<FilesReadResult>(read("files-read-result.json"))
        WireJson.decodeFromString<FilesUploadResult>(read("files-upload-result.json"))
        WireJson.decodeFromString<GitStatusResult>(read("git-status-result.json"))
        WireJson.decodeFromString<GitCommitResult>(read("git-commit-result.json"))
        WireJson.decodeFromString<UsageSummaryResult>(read("usage-summary-result.json"))
        WireJson.decodeFromString<ServersListResult>(read("servers-list-result.json"))
        WireJson.decodeFromString<PreviewOpenResult>(read("preview-open-result.json"))
        WireJson.decodeFromString<WebhooksListResult>(read("webhooks-list-result.json"))
        WireJson.decodeFromString<FontsListResult>(read("fonts-list-result.json"))
        WireJson.decodeFromString<WsEvent>(read("event-agent-update.json"))
        WireJson.decodeFromString<WsEvent>(read("event-agent-removed.json"))
        WireJson.decodeFromString<WsError>(read("ws-error.json"))
        WireJson.decodeFromString<TermFrame>(read("term-frame.json"))
        WireJson.decodeFromString<TermFrame>(read("term-frame-done.json"))
        WireJson.decodeFromString<TermFrame>(read("term-frame-full.json"))
        WireJson.decodeFromString<TermOpenResult>(read("term-open-result.json"))
        WireJson.decodeFromString<PairRequest>(read("pair-request.json"))
        WireJson.decodeFromString<PanesListResult>(read("panes-list-result.json"))
        WireJson.decodeFromString<PanesPeekResult>(read("panes-peek-result.json"))
        WireJson.decodeFromString<PanesSpawnResult>(read("panes-spawn-result.json"))
    }

    @Test
    fun roundTripHelloResult() {
        val text = read("ws-hello-result.json")
        val value = WireJson.decodeFromString<HelloResult>(text)
        val again = WireJson.encodeToString(value)
        assertNotNull(WireJson.decodeFromString<HelloResult>(again))
    }
}
