package sh.simple.android.store

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "item", primaryKeys = ["hostId", "id"])
data class ItemEntity(
    val hostId: String,
    val id: String,
    val json: String,
)

@Dao
interface ItemDao {
    @Query("SELECT * FROM item ORDER BY hostId, id")
    fun observeAll(): Flow<List<ItemEntity>>

    @Query("SELECT * FROM item WHERE hostId = :hostId AND json LIKE '%\"agent\":\"' || :agentId || '\"%'")
    fun observeForAgent(hostId: String, agentId: String): Flow<List<ItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<ItemEntity>)

    @Query("DELETE FROM item WHERE hostId = :hostId")
    suspend fun deleteForHost(hostId: String)

    @Query("SELECT * FROM item")
    suspend fun listAll(): List<ItemEntity>

    @Query("SELECT * FROM item WHERE hostId = :hostId AND id = :id LIMIT 1")
    suspend fun get(hostId: String, id: String): ItemEntity?
}

@Entity(tableName = "message", primaryKeys = ["hostId", "agentId", "id"])
data class MessageEntity(
    val hostId: String,
    val agentId: String,
    val id: String,
    val role: String,
    val ts: Long,
    val json: String,
)

@Dao
interface MessageDao {
    @Query("SELECT * FROM message WHERE hostId = :hostId AND agentId = :agentId ORDER BY ts ASC")
    fun observeForAgent(hostId: String, agentId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM message WHERE hostId = :hostId AND agentId = :agentId ORDER BY ts ASC")
    suspend fun listForAgent(hostId: String, agentId: String): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<MessageEntity>)

    @Query("DELETE FROM message WHERE hostId = :hostId")
    suspend fun deleteForHost(hostId: String)

    @Query("DELETE FROM message WHERE hostId = :hostId AND agentId NOT IN (:keep)")
    suspend fun deleteForHostExcept(hostId: String, keep: List<String>)

    @Query("SELECT id FROM message WHERE hostId = :hostId AND agentId = :agentId")
    suspend fun idsForAgent(hostId: String, agentId: String): List<String>

    @Query("SELECT id FROM message WHERE hostId = :hostId AND agentId = :agentId AND ts < :since")
    suspend fun idsBefore(hostId: String, agentId: String, since: Long): List<String>

    @Query("DELETE FROM message WHERE hostId = :hostId AND agentId = :agentId AND id IN (:ids)")
    suspend fun deleteIds(hostId: String, agentId: String, ids: List<String>)

    @Query("SELECT * FROM message WHERE hostId = :hostId AND agentId = :agentId AND id = :id LIMIT 1")
    suspend fun get(hostId: String, agentId: String, id: String): MessageEntity?
}

@Entity(tableName = "workspace", primaryKeys = ["hostId", "path"])
data class WorkspaceEntity(
    val hostId: String,
    val path: String,
    val name: String,
    val branch: String?,
    val dirty: Boolean,
    val agents: Int,
    val pinned: Boolean,
    val updated: Long,
    val json: String,
)

@Dao
interface WorkspaceDao {
    @Query("SELECT * FROM workspace ORDER BY hostId, name")
    fun observeAll(): Flow<List<WorkspaceEntity>>

    @Query("SELECT * FROM workspace WHERE hostId = :hostId")
    fun observeForHost(hostId: String): Flow<List<WorkspaceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<WorkspaceEntity>)

    @Query("DELETE FROM workspace WHERE hostId = :hostId")
    suspend fun deleteForHost(hostId: String)

    @Query("DELETE FROM workspace WHERE hostId = :hostId AND path = :path")
    suspend fun delete(hostId: String, path: String)

    @Query("SELECT * FROM workspace WHERE hostId = :hostId AND path = :path LIMIT 1")
    suspend fun get(hostId: String, path: String): WorkspaceEntity?
}

@Entity(tableName = "model_catalog", primaryKeys = ["hostId", "kind"])
data class ModelCatalogEntity(
    val hostId: String,
    val kind: String,
    val json: String,
)

@Dao
interface ModelCatalogDao {
    @Query("SELECT * FROM model_catalog WHERE hostId = :hostId AND kind = :kind LIMIT 1")
    suspend fun get(hostId: String, kind: String): ModelCatalogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: ModelCatalogEntity)
}
