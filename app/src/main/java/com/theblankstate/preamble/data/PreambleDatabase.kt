package com.theblankstate.preamble.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Task::class], version = 5, exportSchema = false)
abstract class PreambleDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao

    companion object {
        @Volatile
        private var INSTANCE: PreambleDatabase? = null

        fun getInstance(context: Context): PreambleDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PreambleDatabase::class.java,
                    "preamble_db"
                )
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val columns = mutableSetOf<String>()
                val cursor = db.query("PRAGMA table_info(`tasks`)")
                try {
                    val nameIndex = cursor.getColumnIndex("name")
                    while (cursor.moveToNext()) {
                        if (nameIndex >= 0) {
                            columns.add(cursor.getString(nameIndex))
                        }
                    }
                } finally {
                    cursor.close()
                }

                val createdTimestampExpr = if (columns.contains("createdTimestamp")) {
                    "`createdTimestamp`"
                } else {
                    "strftime('%s','now') * 1000"
                }
                val completedTimestampExpr = if (columns.contains("completedTimestamp")) {
                    "`completedTimestamp`"
                } else {
                    "NULL"
                }
                val deadlineTimeExpr = if (columns.contains("deadlineTime")) {
                    "`deadlineTime`"
                } else {
                    "NULL"
                }
                val updatedTimestampExpr = if (columns.contains("updatedTimestamp")) {
                    "COALESCE(`updatedTimestamp`, $createdTimestampExpr, strftime('%s','now') * 1000)"
                } else {
                    "COALESCE($createdTimestampExpr, strftime('%s','now') * 1000)"
                }

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `tasks_new` (
                        `id` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `isCompleted` INTEGER NOT NULL,
                        `createdDate` TEXT NOT NULL,
                        `createdTimestamp` INTEGER NOT NULL,
                        `completedTimestamp` INTEGER,
                        `deadlineTime` TEXT,
                        `updatedTimestamp` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    INSERT INTO `tasks_new` (
                        `id`,
                        `title`,
                        `isCompleted`,
                        `createdDate`,
                        `createdTimestamp`,
                        `completedTimestamp`,
                        `deadlineTime`,
                        `updatedTimestamp`
                    )
                    SELECT
                        CAST(`id` AS TEXT),
                        `title`,
                        `isCompleted`,
                        `createdDate`,
                        $createdTimestampExpr,
                        $completedTimestampExpr,
                        $deadlineTimeExpr,
                        $updatedTimestampExpr
                    FROM `tasks`
                    """.trimIndent()
                )

                db.execSQL("DROP TABLE `tasks`")
                db.execSQL("ALTER TABLE `tasks_new` RENAME TO `tasks`")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add 'source' column with default value 'local'
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `source` TEXT NOT NULL DEFAULT 'local'")
            }
        }
    }
}
