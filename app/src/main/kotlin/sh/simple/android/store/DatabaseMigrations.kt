package sh.simple.android.store

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `message_image` (
                `hostId` TEXT NOT NULL,
                `agentId` TEXT NOT NULL,
                `messageId` TEXT NOT NULL,
                `slot` INTEGER NOT NULL,
                `localPath` TEXT NOT NULL,
                PRIMARY KEY(`hostId`, `agentId`, `messageId`, `slot`)
            )
            """.trimIndent(),
        )
    }
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `model_catalog` (
                `hostId` TEXT NOT NULL,
                `kind` TEXT NOT NULL,
                `json` TEXT NOT NULL,
                PRIMARY KEY(`hostId`, `kind`)
            )
            """.trimIndent(),
        )
    }
}
