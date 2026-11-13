package sh.simple.android.store

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "agent", primaryKeys = ["hostId", "id"])
data class AgentEntity(
    val hostId: String,
    val id: String,
    val kind: String,
    val title: String,
    val cwd: String,
    val workspace: String,
    val status: String,
    val waiting: String?,
    val model: String?,
    val mode: String?,
    val updated: Long,
    val pinned: Boolean,
    val unread: Boolean,
    val json: String,
)

@Dao
interface AgentDao {
    @Query("SELECT * FROM agent WHERE hostId = :hostId ORDER BY updated DESC")
    fun observeForHost(hostId: String): Flow<List<AgentEntity>>

    @Query("SELECT * FROM agent ORDER BY hostId, updated DESC")
    fun observeAll(): Flow<List<AgentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<AgentEntity>)

    @Query("SELECT COUNT(*) FROM agent WHERE hostId = :hostId")
    suspend fun countForHost(hostId: String): Int

    @Query("DELETE FROM agent WHERE hostId = :hostId")
    suspend fun deleteForHost(hostId: String)

    @Query("DELETE FROM agent WHERE hostId = :hostId AND id = :id")
    suspend fun delete(hostId: String, id: String)

    @Query("SELECT * FROM agent")
    suspend fun listAll(): List<AgentEntity>

    @Query("SELECT * FROM agent WHERE hostId = :hostId AND id = :id LIMIT 1")
    suspend fun get(hostId: String, id: String): AgentEntity?
}
