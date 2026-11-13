package sh.simple.android.store

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import sh.simple.android.model.Agent
import sh.simple.android.model.AgentRemoved
import sh.simple.android.model.HelloResult
import sh.simple.android.model.Item
import sh.simple.android.model.Message
import sh.simple.android.model.WireJson
import sh.simple.android.model.WsEvent
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class SyncTest {
    private lateinit var db: SimpleDatabase
    private lateinit var sync: Sync

    private fun fixture(name: String): String {
        val root = File(System.getProperty("user.dir")).parentFile!!.resolve("proto/fixtures")
        return root.resolve(name).readText()
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SimpleDatabase::class.java,
        ).allowMainThreadQueries().build()
        sync = Sync(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun applyHelloSnapshotStoresAgentsForHomeCount() = runTest {
        db.hosts().upsert(
            HostEntity(
                id = "h_deadbeef",
                name = "simple",
                addresses = "127.0.0.1:47420",
                fingerprint = "ab",
                tokenCipher = "x",
                epoch = null,
                seq = 0,
            ),
        )
        val hello = WireJson.decodeFromString<HelloResult>(fixture("ws-hello-result-snapshot.json"))
        sync.applyHello("h_deadbeef", hello)
        assertEquals(3, sync.countAgents("h_deadbeef"))
        assertEquals(3, db.agents().observeAll().first().size)
    }

    @Test
    fun applyHelloSnapshotKeepsCachedMessages() = runTest {
        val hostId = "h_deadbeef"
        db.hosts().upsert(
            HostEntity(
                id = hostId,
                name = "simple",
                addresses = "127.0.0.1:47420",
                fingerprint = "ab",
                tokenCipher = "x",
                epoch = "e1",
                seq = 1,
            ),
        )
        val msg = Message("m1", "user", 1, text = "hello")
        db.messages().upsertAll(
            listOf(
                MessageEntity(hostId, "t3", "m1", "user", 1, WireJson.encodeToString(msg)),
                MessageEntity(hostId, "gone", "m2", "user", 1, WireJson.encodeToString(msg)),
            ),
        )
        val hello = WireJson.decodeFromString<HelloResult>(fixture("ws-hello-result-snapshot.json"))
        sync.applyHello(hostId, hello)
        assertEquals(1, db.messages().listForAgent(hostId, "t3").size)
        assertEquals(0, db.messages().listForAgent(hostId, "gone").size)
    }

    @Test
    fun agentUpdateEventDecodes() {
        val event = WireJson.decodeFromString<WsEvent>(fixture("event-agent-update.json"))
        val agent = WireJson.decodeFromJsonElement(Agent.serializer(), event.d)
        assertEquals("t4", agent.id)
        assertEquals("claude", agent.kind)
    }

    @Test
    fun duplicateAgentUpdateSkipsWrite() = runTest {
        db.hosts().upsert(
            HostEntity(
                id = "h1",
                name = "simple",
                addresses = "127.0.0.1:47420",
                fingerprint = "ab",
                tokenCipher = "x",
                epoch = "e1",
                seq = 1,
            ),
        )
        val event = WireJson.decodeFromString<WsEvent>(fixture("event-agent-update.json"))
        sync.applyEvent("h1", event)
        val json = db.agents().get("h1", "t4")!!.json
        sync.applyEvent("h1", event.copy(seq = 2))
        assertEquals(json, db.agents().get("h1", "t4")!!.json)
    }

    @Test
    fun agentUpdateUpsertsLive() = runTest {
        db.hosts().upsert(
            HostEntity(
                id = "h1",
                name = "simple",
                addresses = "127.0.0.1:47420",
                fingerprint = "ab",
                tokenCipher = "x",
                epoch = "e1",
                seq = 1,
            ),
        )
        val event = WireJson.decodeFromString<WsEvent>(fixture("event-agent-update.json"))
        sync.applyEvent("h1", event)
        assertEquals(1, sync.countAgents("h1"))
        assertEquals("t4", db.agents().observeForHost("h1").first().single().id)
    }

    @Test
    fun inboxUpdateNotifiesListener() = runTest {
        db.hosts().upsert(
            HostEntity(
                id = "h1",
                name = "simple",
                addresses = "127.0.0.1:47420",
                fingerprint = "ab",
                tokenCipher = "x",
                epoch = "e1",
                seq = 1,
            ),
        )
        var seen: Item? = null
        val syncWithUpdate = Sync(db, onInboxUpdate = { _, item -> seen = item })
        val add = WireJson.decodeFromString<WsEvent>(fixture("event-inbox-add.json"))
        syncWithUpdate.applyEvent("h1", add)
        val update = add.copy(
            ev = "inbox.update",
            d = WireJson.parseToJsonElement(
                """{"id":"i_abc","agent":"t4","kind":"approval","title":"Clean build","command":"npm run build","tool":"Bash","created_ms":1700000003000,"read":true,"resolved":true}""",
            ),
        )
        syncWithUpdate.applyEvent("h1", update)
        assertEquals(true, seen?.resolved)
        assertEquals("i_abc", seen?.id)
    }
}
