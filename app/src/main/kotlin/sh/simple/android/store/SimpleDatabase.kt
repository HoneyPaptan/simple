package sh.simple.android.store

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        HostEntity::class,
        AgentEntity::class,
        ItemEntity::class,
        MessageEntity::class,
        WorkspaceEntity::class,
        ModelCatalogEntity::class,
        MessageImageEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class SimpleDatabase : RoomDatabase() {
    abstract fun hosts(): HostDao
    abstract fun agents(): AgentDao
    abstract fun items(): ItemDao
    abstract fun messages(): MessageDao
    abstract fun workspaces(): WorkspaceDao
    abstract fun modelCatalog(): ModelCatalogDao
    abstract fun messageImages(): MessageImageDao

    companion object {
        fun open(context: Context): SimpleDatabase =
            Room.databaseBuilder(context, SimpleDatabase::class.java, "simple.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
