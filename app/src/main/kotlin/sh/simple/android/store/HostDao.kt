package sh.simple.android.store

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "host")
data class HostEntity(
    @PrimaryKey val id: String,
    val name: String,
    val addresses: String,
    val fingerprint: String,
    val tokenCipher: String,
    val epoch: String?,
    val seq: Long,
)

@Dao
interface HostDao {
    @Query("SELECT * FROM host ORDER BY name")
    suspend fun listAll(): List<HostEntity>

    @Query("SELECT * FROM host ORDER BY name")
    fun observeAll(): Flow<List<HostEntity>>

    @Query("SELECT * FROM host WHERE id = :id")
    suspend fun get(id: String): HostEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(host: HostEntity)

    @Query("UPDATE host SET epoch = :epoch, seq = :seq WHERE id = :id")
    suspend fun updateCursor(id: String, epoch: String?, seq: Long)

    @Query("DELETE FROM host WHERE id = :id")
    suspend fun delete(id: String)
}
