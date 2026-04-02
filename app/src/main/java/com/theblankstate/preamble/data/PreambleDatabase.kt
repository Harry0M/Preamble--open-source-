package com.theblankstate.preamble.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Task::class, TaskTagOverride::class], version = 16, exportSchema = false)
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
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15)
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

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add 'deletedFromGoogle' column with default 0 (false)
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `deletedFromGoogle` INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `priority` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `description` TEXT")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `recurrenceType` TEXT")
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `recurrenceInterval` INTEGER")
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `recurrenceDays` TEXT")
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `recurrenceEndDate` TEXT")
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `recurrenceParentId` TEXT")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `parentTaskId` TEXT")
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `tags` TEXT")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `googleCalendarId` TEXT")
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add display-only recurrence info column
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `googleRecurrenceInfo` TEXT")
                // Create tag overrides table for persistent tag storage
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `task_tag_overrides` (
                        `googleId` TEXT NOT NULL,
                        `tags` TEXT NOT NULL,
                        `updatedTimestamp` INTEGER NOT NULL,
                        PRIMARY KEY(`googleId`)
                    )
                """.trimIndent())
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add Google Calendar event metadata columns
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `eventType` TEXT")
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `calendarName` TEXT")
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `location` TEXT")
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `endTime` TEXT")
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `meetingLink` TEXT")
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add optimistic UI sync status columns
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `isSyncing` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `syncFailed` INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Independent alarm management columns
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `customAlarmTimeMs` INTEGER")
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `isAlarmPaused` INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Extended Google Calendar metadata columns
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `attendeesJson` TEXT")
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `remindersJson` TEXT")
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `htmlLink` TEXT")
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `organizerJson` TEXT")
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `visibility` TEXT")
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `attachmentsJson` TEXT")
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `conferencePhone` TEXT")
                // Extended Google Tasks metadata columns
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `webViewLink` TEXT")
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `taskLinksJson` TEXT")
            }
        }
    }
}
