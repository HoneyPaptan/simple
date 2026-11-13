package sh.simple.android.store

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "message_image", primaryKeys = ["hostId", "agentId", "messageId", "slot"])
data class MessageImageEntity(
    val hostId: String,
    val agentId: String,
    val messageId: String,
    val slot: Int,
    val localPath: String,
)

@Dao
interface MessageImageDao {
    @Query(
        "SELECT * FROM message_image WHERE hostId = :hostId AND agentId = :agentId ORDER BY messageId, slot",
    )
    fun observeForAgent(hostId: String, agentId: String): Flow<List<MessageImageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<MessageImageEntity>)

    @Query(
        "UPDATE message_image SET messageId = :toId WHERE hostId = :hostId AND agentId = :agentId AND messageId = :fromId",
    )
    suspend fun rebindMessageId(hostId: String, agentId: String, fromId: String, toId: String)

    @Query("DELETE FROM message_image WHERE hostId = :hostId")
    suspend fun deleteForHost(hostId: String)
}
