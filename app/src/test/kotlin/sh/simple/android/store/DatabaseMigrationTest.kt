package sh.simple.android.store

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class DatabaseMigrationTest {
    @Test
    fun migrate1To2KeepsHosts() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migrate-${UUID.randomUUID()}"
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { sql ->
            sql.execSQL(
                "CREATE TABLE IF NOT EXISTS `host` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                    "`addresses` TEXT NOT NULL, `fingerprint` TEXT NOT NULL, `tokenCipher` TEXT NOT NULL, " +
                    "`epoch` TEXT, `seq` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )
            sql.execSQL(
                "CREATE TABLE IF NOT EXISTS `agent` (`hostId` TEXT NOT NULL, `id` TEXT NOT NULL, " +
                    "`kind` TEXT NOT NULL, `title` TEXT NOT NULL, `cwd` TEXT NOT NULL, `workspace` TEXT NOT NULL, " +
                    "`status` TEXT NOT NULL, `waiting` TEXT, `model` TEXT, `mode` TEXT, `updated` INTEGER NOT NULL, " +
                    "`pinned` INTEGER NOT NULL, `unread` INTEGER NOT NULL, `json` TEXT NOT NULL, " +
                    "PRIMARY KEY(`hostId`, `id`))",
            )
            sql.execSQL(
                "CREATE TABLE IF NOT EXISTS `item` (`hostId` TEXT NOT NULL, `id` TEXT NOT NULL, " +
                    "`json` TEXT NOT NULL, PRIMARY KEY(`hostId`, `id`))",
            )
            sql.execSQL(
                "CREATE TABLE IF NOT EXISTS `message` (`hostId` TEXT NOT NULL, `agentId` TEXT NOT NULL, " +
                    "`id` TEXT NOT NULL, `role` TEXT NOT NULL, `ts` INTEGER NOT NULL, `json` TEXT NOT NULL, " +
                    "PRIMARY KEY(`hostId`, `agentId`, `id`))",
            )
            sql.execSQL(
                "CREATE TABLE IF NOT EXISTS `workspace` (`hostId` TEXT NOT NULL, `path` TEXT NOT NULL, " +
                    "`name` TEXT NOT NULL, `branch` TEXT, `dirty` INTEGER NOT NULL, `agents` INTEGER NOT NULL, " +
                    "`pinned` INTEGER NOT NULL, `updated` INTEGER NOT NULL, `json` TEXT NOT NULL, " +
                    "PRIMARY KEY(`hostId`, `path`))",
            )
            sql.execSQL(
                "INSERT INTO host (id, name, addresses, fingerprint, tokenCipher, epoch, seq) " +
                    "VALUES ('h1', 'box', '127.0.0.1:47420', 'fp', 'cipher', 'e1', 3)",
            )
            sql.version = 1
        }
        val db = Room.databaseBuilder(context, SimpleDatabase::class.java, name)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()
        val hosts = db.hosts().listAll()
        assertEquals(1, hosts.size)
        assertEquals("box", hosts.single().name)
        db.modelCatalog().upsert(ModelCatalogEntity("h1", "claude", "{}"))
        assertEquals("{}", db.modelCatalog().get("h1", "claude")?.json)
        db.close()
        context.deleteDatabase(name)
    }

    @Test
    fun migrate2To3AddsMessageImages() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migrate-${UUID.randomUUID()}"
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { sql ->
            sql.execSQL(
                "CREATE TABLE IF NOT EXISTS `host` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                    "`addresses` TEXT NOT NULL, `fingerprint` TEXT NOT NULL, `tokenCipher` TEXT NOT NULL, " +
                    "`epoch` TEXT, `seq` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )
            sql.execSQL(
                "CREATE TABLE IF NOT EXISTS `agent` (`hostId` TEXT NOT NULL, `id` TEXT NOT NULL, " +
                    "`kind` TEXT NOT NULL, `title` TEXT NOT NULL, `cwd` TEXT NOT NULL, `workspace` TEXT NOT NULL, " +
                    "`status` TEXT NOT NULL, `waiting` TEXT, `model` TEXT, `mode` TEXT, `updated` INTEGER NOT NULL, " +
                    "`pinned` INTEGER NOT NULL, `unread` INTEGER NOT NULL, `json` TEXT NOT NULL, " +
                    "PRIMARY KEY(`hostId`, `id`))",
            )
            sql.execSQL(
                "CREATE TABLE IF NOT EXISTS `item` (`hostId` TEXT NOT NULL, `id` TEXT NOT NULL, " +
                    "`json` TEXT NOT NULL, PRIMARY KEY(`hostId`, `id`))",
            )
            sql.execSQL(
                "CREATE TABLE IF NOT EXISTS `message` (`hostId` TEXT NOT NULL, `agentId` TEXT NOT NULL, " +
                    "`id` TEXT NOT NULL, `role` TEXT NOT NULL, `ts` INTEGER NOT NULL, `json` TEXT NOT NULL, " +
                    "PRIMARY KEY(`hostId`, `agentId`, `id`))",
            )
            sql.execSQL(
                "CREATE TABLE IF NOT EXISTS `workspace` (`hostId` TEXT NOT NULL, `path` TEXT NOT NULL, " +
                    "`name` TEXT NOT NULL, `branch` TEXT, `dirty` INTEGER NOT NULL, `agents` INTEGER NOT NULL, " +
                    "`pinned` INTEGER NOT NULL, `updated` INTEGER NOT NULL, `json` TEXT NOT NULL, " +
                    "PRIMARY KEY(`hostId`, `path`))",
            )
            sql.execSQL(
                "CREATE TABLE IF NOT EXISTS `model_catalog` (`hostId` TEXT NOT NULL, `kind` TEXT NOT NULL, " +
                    "`json` TEXT NOT NULL, PRIMARY KEY(`hostId`, `kind`))",
            )
            sql.version = 2
        }
        val db = Room.databaseBuilder(context, SimpleDatabase::class.java, name)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()
        db.messageImages().upsertAll(
            listOf(
                MessageImageEntity("h", "a", "m1", 1, "/tmp/one.jpg"),
            ),
        )
        assertEquals(1, db.messageImages().observeForAgent("h", "a").first().size)
        db.close()
        context.deleteDatabase(name)
    }
}
